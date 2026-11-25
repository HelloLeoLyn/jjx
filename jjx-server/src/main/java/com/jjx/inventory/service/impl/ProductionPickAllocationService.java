package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.StockItemStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryRollService;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.service.ReviewFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 方案 C：单据批次分配，不新增、消费或释放任何预占。dev-20261125-004。 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProductionPickAllocationService {
    private static final String REVIEW_BIZ = "production_pick_mix";
    private final InventoryOutboundOrderMapper orders;
    private final InventoryOutboundItemMapper items;
    private final InventoryStockItemMapper stocks;
    private final MaterialStockAllocationService reservations;
    private final InventoryRollService rolls;
    private final JdbcTemplate jdbc;
    private final ReviewFlowService reviews;

    public record AllocationLine(Long demandItemId, Long stockItemId, BigDecimal quantity) {}
    public record SaveRequest(String expectedFingerprint, List<AllocationLine> lines) {}
    public record ReviewRequest(String expectedFingerprint, boolean approved, String reason) {}
    public record Supplier(Long id, String name) {}
    public record Batch(Long stockItemId, Long materialId, Long inventoryItemId, Long warehouseId,
                        Long locationId, String locationName, String batchNo, String rollNo,
                        BigDecimal rollWidthMm, BigDecimal originalRollLengthM, BigDecimal quantity,
                        BigDecimal availableQuantity, Long supplierId, String supplierName) {}
    public record Demand(Long demandItemId, Long materialId, String materialCode, String materialName,
                         String specification, String unit, Long sourceBomItemId, BigDecimal quantity,
                         BigDecimal coverageQuantity, List<InventoryOutboundItem> allocations) {}
    public record AllocationView(Long outboundId, Integer status, String fingerprint, boolean mixedSupplier,
                                 boolean unknownSupplier, List<Demand> demands, List<Batch> batches) {}

    public static boolean isProductionPick(InventoryOutboundOrder order) {
        return order != null && "production".equals(order.getOutboundType()) && "work_order".equals(order.getSourceType());
    }

    private InventoryOutboundOrder lock(Long id) {
        InventoryOutboundOrder order = orders.selectByIdForUpdate(id);
        if (!isProductionPick(order)) throw new BusinessException("仅生产工单领料单可分配库位/批次");
        Integer status = order.getOrderStatus();
        if (status == null || !Set.of(InventoryOrderStatusEnum.DRAFT.getValue(), InventoryOrderStatusEnum.PENDING.getValue(),
                InventoryOrderStatusEnum.APPROVED.getValue(), InventoryOrderStatusEnum.REJECTED.getValue(),
                InventoryOrderStatusEnum.MIXED_BATCH_PENDING.getValue()).contains(status))
            throw new BusinessException("仅未发料的生产领料单可分配或保存");
        if (order.getWarehouseId() == null) throw new BusinessException("生产领料单缺少仓库");
        return order;
    }

    @Transactional(readOnly = true)
    public AllocationView view(Long id) {
        InventoryOutboundOrder order = orders.selectById(id);
        if (!isProductionPick(order)) throw new BusinessException("仅生产工单领料单可查看分配");
        return snapshot(order, items.selectByOutboundId(id));
    }

    /** 同一需求的数量始终是已分配行与唯一欠料行之和；BOM及替代依据作为分组身份。 */
    private List<List<InventoryOutboundItem>> groups(List<InventoryOutboundItem> source) {
        Map<String, List<InventoryOutboundItem>> grouped = new LinkedHashMap<>();
        for (InventoryOutboundItem item : source) {
            String key = Arrays.asList(item.getMaterialId(), item.getSourceBomItemId(), item.getSubstituteOfMaterialId(),
                    item.getUnit(), item.getAllocationRatio(), item.getAllocationLossRate(), item.getAllocationReason()).toString();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        for (List<InventoryOutboundItem> group : grouped.values()) group.sort(Comparator.comparing(InventoryOutboundItem::getItemId));
        List<List<InventoryOutboundItem>> result = new ArrayList<>(grouped.values());
        result.sort(Comparator.comparing(g -> g.getFirst().getItemId()));
        return result;
    }

    private BigDecimal total(List<InventoryOutboundItem> group) {
        return group.stream().map(i -> zero(i.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal coverage(List<InventoryOutboundItem> group) {
        return group.stream().map(InventoryOutboundItem::getCoverageQuantity).filter(Objects::nonNull)
                .reduce(BigDecimal::add).orElse(null);
    }

    private BigDecimal available(InventoryOutboundOrder order, InventoryStockItem stock) {
        BigDecimal reserved = zero(stock.getReservedQuantity());
        BigDecimal owned = zero(reservations.ownedQuantity(order, stock.getItemId())).min(reserved).max(BigDecimal.ZERO);
        return zero(stock.getQuantity()).subtract(reserved.subtract(owned)).max(BigDecimal.ZERO);
    }

    private List<InventoryStockItem> candidates(InventoryOutboundOrder order, Long materialId) {
        return stocks.selectProductionPickCandidates(materialId, order.getWarehouseId(), StockItemStatusEnum.ACTIVE.getValue());
    }

    public Map<String, Object> allocate(Long id) {
        InventoryOutboundOrder order = lock(id);
        List<InventoryOutboundItem> before = items.selectByOutboundId(id);
        Map<Long, BigDecimal> used = new HashMap<>();
        List<AllocationLine> plan = new ArrayList<>();
        for (List<InventoryOutboundItem> group : groups(before)) {
            InventoryOutboundItem root = group.getFirst();
            BigDecimal remaining = total(group);
            for (InventoryStockItem stock : candidates(order, root.getMaterialId())) {
                BigDecimal capacity = available(order, stock).subtract(used.getOrDefault(stock.getItemId(), BigDecimal.ZERO));
                if (remaining.signum() <= 0) break;
                if (capacity.signum() <= 0 || stock.getLocationId() == null || stock.getBatchNo() == null) continue;
                // 卷材只取完整原卷；超出本次需求的卷不自动超领，也不拆卷。
                BigDecimal take = stock.getRollNo() == null ? remaining.min(capacity) : zero(stock.getQuantity());
                if (take.signum() <= 0 || take.compareTo(capacity) > 0 || take.compareTo(remaining) > 0) continue;
                plan.add(new AllocationLine(root.getItemId(), stock.getItemId(), take));
                used.merge(stock.getItemId(), take, BigDecimal::add);
                remaining = remaining.subtract(take);
            }
        }
        savePlan(order, before, plan);
        AllocationView view = snapshot(order, items.selectByOutboundId(id));
        long allocated = view.demands().stream().flatMap(d -> d.allocations().stream()).filter(i -> i.getStockItemId() != null).count();
        long shortage = view.demands().stream().flatMap(d -> d.allocations().stream()).filter(i -> i.getStockItemId() == null).count();
        return Map.of("allocatedLines", allocated, "shortageLines", shortage, "allocation", view);
    }

    public AllocationView save(Long id, SaveRequest request) {
        InventoryOutboundOrder order = lock(id);
        List<InventoryOutboundItem> before = items.selectByOutboundId(id);
        expect(request.expectedFingerprint(), snapshot(order, before).fingerprint());
        if (request.lines() == null) throw new BusinessException("缺少分配明细");
        savePlan(order, before, request.lines());
        return snapshot(order, items.selectByOutboundId(id));
    }

    private void savePlan(InventoryOutboundOrder order, List<InventoryOutboundItem> before, List<AllocationLine> plan) {
        String previous = snapshot(order, before).fingerprint();
        Map<Long, List<AllocationLine>> byDemand = new LinkedHashMap<>();
        Set<Long> demandIds = new HashSet<>();
        for (List<InventoryOutboundItem> group : groups(before)) demandIds.add(group.getFirst().getItemId());
        Map<Long, BigDecimal> used = new HashMap<>();
        Map<Long, InventoryStockItem> locked = new TreeMap<>();
        for (AllocationLine line : plan) {
            if (line == null || !demandIds.contains(line.demandItemId()) || line.stockItemId() == null
                    || line.quantity() == null || line.quantity().signum() < 0 || line.quantity().stripTrailingZeros().scale() > 4)
                throw new BusinessException("分配明细身份、数量或精度不正确");
            if (line.quantity().signum() == 0) continue;
            byDemand.computeIfAbsent(line.demandItemId(), k -> new ArrayList<>()).add(line);
            used.merge(line.stockItemId(), line.quantity(), BigDecimal::add);
        }
        // 所有批次按固定顺序加锁；先全量校验，再写任何明细。
        for (Long stockId : new TreeSet<>(used.keySet())) {
            InventoryStockItem stock = stocks.selectByIdForUpdate(stockId);
            validateBatch(order, stock, null);
            if (used.get(stockId).compareTo(available(order, stock)) > 0) insufficient(stock, used.get(stockId).subtract(available(order, stock)));
            if (stock.getRollNo() != null && used.get(stockId).compareTo(stock.getQuantity()) != 0)
                throw new BusinessException("卷材必须整卷分配，禁止拆半卷：" + stock.getRollNo());
            locked.put(stockId, stock);
        }
        int sortOrder = 0;
        for (List<InventoryOutboundItem> group : groups(before)) {
            InventoryOutboundItem root = group.getFirst();
            BigDecimal demand = total(group);
            Map<Long, BigDecimal> merged = new LinkedHashMap<>();
            for (AllocationLine line : byDemand.getOrDefault(root.getItemId(), List.of())) {
                InventoryStockItem stock = locked.get(line.stockItemId());
                validateBatch(order, stock, root);
                if (stock.getRollNo() != null && line.quantity().compareTo(stock.getQuantity()) != 0)
                    throw new BusinessException("同一卷不得拆分到多条需求，请按整卷领料");
                merged.merge(line.stockItemId(), line.quantity(), BigDecimal::add);
            }
            BigDecimal assigned = merged.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (assigned.compareTo(demand) > 0) throw new BusinessException("物料 " + root.getMaterialCode() + " 分配超过原需求");
            List<InventoryOutboundItem> desired = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> entry : merged.entrySet()) {
                InventoryOutboundItem row = copy(root);
                bind(row, locked.get(entry.getKey()), entry.getValue());
                desired.add(row);
            }
            if (assigned.compareTo(demand) < 0 || desired.isEmpty()) {
                InventoryOutboundItem shortage = copy(root);
                bind(shortage, null, demand.subtract(assigned));
                desired.add(shortage);
            }
            BigDecimal covered = coverage(group);
            for (int index = 0; index < desired.size(); index++) {
                InventoryOutboundItem row = desired.get(index);
                row.setCoverageQuantity(index == 0 ? covered : null);
                row.setSortOrder(++sortOrder);
                if (index < group.size()) {
                    row.setItemId(group.get(index).getItemId());
                    updateRow(row);
                } else {
                    row.setItemId(null);
                    if (items.insert(row) != 1) throw new BusinessException("新增分配行失败");
                }
            }
            for (int index = desired.size(); index < group.size(); index++) {
                if (items.deleteById(group.get(index).getItemId()) != 1) throw new BusinessException("移除过期分配行失败");
            }
        }
        List<InventoryOutboundItem> after = items.selectByOutboundId(order.getOutboundId());
        BigDecimal sumBefore = total(before), sumAfter = total(after);
        if (sumBefore.compareTo(sumAfter) != 0) throw new BusinessException("分配需求不守恒，已撤销本次保存");
        order.setTotalQuantity(sumAfter);
        order.setTotalAmount(after.stream().anyMatch(i -> zero(i.getQuantity()).signum() > 0 && i.getAmount() == null)
                ? null : after.stream().map(i -> zero(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        AllocationView view = snapshot(order, after);
        if (!previous.equals(view.fingerprint())) {
            boolean approval = view.mixedSupplier() || view.unknownSupplier();
            Integer from = order.getOrderStatus();
            if (approval) order.setOrderStatus(InventoryOrderStatusEnum.MIXED_BATCH_PENDING.getValue());
            else if (InventoryOrderStatusEnum.MIXED_BATCH_PENDING.getValue().equals(from))
                order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
            record(order, approval ? "SUBMIT" : "ALLOCATE", "领料批次分配", from, "分配内容变更，既有混批审批失效", view.fingerprint());
        }
        if (orders.update(order, new UpdateWrapper<InventoryOutboundOrder>().eq("outbound_id", order.getOutboundId())
                .set("total_amount", order.getTotalAmount())) != 1) throw new BusinessException("更新领料单失败");
    }

    private void updateRow(InventoryOutboundItem row) {
        // 明确清空旧库位/批次及额外行抵扣量，避免 NOT_NULL 策略留下旧分配。
        UpdateWrapper<InventoryOutboundItem> update = new UpdateWrapper<InventoryOutboundItem>().eq("item_id", row.getItemId())
                .set("stock_item_id", row.getStockItemId()).set("inventory_item_id", row.getInventoryItemId())
                .set("location_id", row.getLocationId()).set("batch_no", row.getBatchNo()).set("roll_no", row.getRollNo())
                .set("measurement_snapshot", row.getMeasurementSnapshot()).set("coverage_quantity", row.getCoverageQuantity())
                .set("unit_price", row.getUnitPrice()).set("amount", row.getAmount());
        if (items.update(row, update) != 1) throw new BusinessException("保存分配行失败");
    }

    private InventoryOutboundItem copy(InventoryOutboundItem root) {
        InventoryOutboundItem row = new InventoryOutboundItem();
        BeanUtils.copyProperties(root, row);
        row.setItemId(null);
        return row;
    }

    private void bind(InventoryOutboundItem row, InventoryStockItem stock, BigDecimal quantity) {
        row.setQuantity(quantity);
        row.setStockItemId(stock == null ? null : stock.getItemId());
        row.setLocationId(stock == null ? null : stock.getLocationId());
        row.setBatchNo(stock == null ? null : stock.getBatchNo());
        row.setRollNo(stock == null ? null : stock.getRollNo());
        row.setMeasurementSnapshot(stock == null ? null : stock.getMeasurementSnapshot());
        if (stock != null) row.setInventoryItemId(stock.getInventoryItemId());
        row.setUnitPrice(stock == null ? null : stock.getUnitCost());
        row.setAmount(row.getUnitPrice() == null ? null : quantity.multiply(row.getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private void validateBatch(InventoryOutboundOrder order, InventoryStockItem stock, InventoryOutboundItem row) {
        if (stock == null || !Objects.equals(stock.getWarehouseId(), order.getWarehouseId())
                || stock.getLocationId() == null || stock.getBatchNo() == null || !StockItemStatusEnum.ACTIVE.getValue().equals(stock.getStatus())
                || (row != null && (!Objects.equals(row.getMaterialId(), stock.getMaterialId())
                || (row.getInventoryItemId() != null && !Objects.equals(row.getInventoryItemId(), stock.getInventoryItemId())))))
            throw new BusinessException("批次物料、仓库或库位不符，请重排");
    }

    public void validateApproval(InventoryOutboundOrder order) {
        AllocationView view = snapshot(order, items.selectByOutboundId(order.getOutboundId()));
        ensureKnown(view);
        if (view.mixedSupplier() && !approved(view)) throw new BusinessException("跨供应商混批需专门审批，不能通过普通审核绕过");
    }

    public void validateForIssue(InventoryOutboundOrder order, List<InventoryOutboundItem> source) {
        if (source.isEmpty() || source.stream().noneMatch(row -> zero(row.getQuantity()).signum() > 0))
            throw new BusinessException("领料明细为空，不能发料");
        AllocationView view = snapshot(order, source);
        ensureKnown(view);
        if (view.mixedSupplier() && !approved(view)) throw new BusinessException("跨供应商混批尚未审批或分配已变更，请重新审批");
        Map<Long, BigDecimal> required = new TreeMap<>();
        for (InventoryOutboundItem row : source) {
            if (zero(row.getQuantity()).signum() <= 0) continue;
            if (row.getStockItemId() == null) throw new BusinessException("物料 " + row.getMaterialCode() + " 尚有欠料，请重排");
            required.merge(row.getStockItemId(), row.getQuantity(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : required.entrySet()) {
            InventoryStockItem stock = stocks.selectByIdForUpdate(entry.getKey());
            validateBatch(order, stock, null);
            if (entry.getValue().compareTo(available(order, stock)) > 0) insufficient(stock, entry.getValue().subtract(available(order, stock)));
        }
    }

    public InventoryStockItem issueStock(InventoryOutboundOrder order, InventoryOutboundItem row) {
        InventoryStockItem stock = stocks.selectByIdForUpdate(row.getStockItemId());
        validateBatch(order, stock, row);
        if (!Objects.equals(row.getLocationId(), stock.getLocationId()) || !Objects.equals(row.getBatchNo(), stock.getBatchNo())
                || !Objects.equals(row.getRollNo(), stock.getRollNo())) throw new BusinessException("批次身份已变化，请重排");
        if (row.getQuantity().compareTo(available(order, stock)) > 0) insufficient(stock, row.getQuantity().subtract(available(order, stock)));
        if (stock.getRollNo() != null) return rolls.issueStock(order, row);
        bind(row, stock, row.getQuantity());
        return stock;
    }

    public AllocationView review(Long id, ReviewRequest request) {
        InventoryOutboundOrder order = lock(id);
        if (!InventoryOrderStatusEnum.MIXED_BATCH_PENDING.getValue().equals(order.getOrderStatus()))
            throw new BusinessException("当前单据不在待混批审批状态");
        AllocationView view = snapshot(order, items.selectByOutboundId(id));
        expect(request.expectedFingerprint(), view.fingerprint());
        if (!request.approved() && (request.reason() == null || request.reason().isBlank())) throw new BusinessException("驳回必须填写原因");
        if (request.reason() != null && request.reason().length() > 900) throw new BusinessException("审批意见不能超过900字");
        if (request.approved()) ensureKnown(view);
        Integer from = order.getOrderStatus();
        order.setOrderStatus(request.approved() ? InventoryOrderStatusEnum.APPROVED.getValue() : InventoryOrderStatusEnum.REJECTED.getValue());
        record(order, request.approved() ? "APPROVE" : "REJECT", request.approved() ? "混批审批通过" : "混批审批驳回", from, request.reason(), view.fingerprint());
        if (orders.updateById(order) != 1) throw new BusinessException("保存混批审批失败");
        return snapshot(order, items.selectByOutboundId(id));
    }

    private boolean approved(AllocationView view) {
        List<ReviewFlow> flows = reviews.listByBiz(REVIEW_BIZ, view.outboundId());
        if (flows.isEmpty()) return false;
        ReviewFlow last = flows.getLast();
        return "APPROVE".equals(last.getActionCode()) && view.fingerprint().equals(last.getExtraJson());
    }

    private void record(InventoryOutboundOrder order, String action, String name, Integer from, String comment, String fingerprint) {
        ReviewFlow flow = reviews.record(REVIEW_BIZ, order.getOutboundId(), action, name, from, order.getOrderStatus(), comment, null);
        flow.setExtraJson(fingerprint);
        if (!reviews.updateById(flow)) throw new BusinessException("保存审批分配快照失败");
    }

    private void ensureKnown(AllocationView view) {
        if (view.unknownSupplier()) throw new BusinessException("批次供应商未知，禁止审批或发料，请补齐入库来源后重排");
    }

    private void expect(String expected, String actual) {
        if (expected == null || !expected.equals(actual)) throw new BusinessException("分配已被修改，请刷新后重试");
    }

    private void insufficient(InventoryStockItem stock, BigDecimal shortage) {
        throw new BusinessException("物料 " + stock.getMaterialCode() + " 批次 " + stock.getBatchNo() + " 不足，缺口 " + shortage.toPlainString() + "，请重排");
    }

    private AllocationView snapshot(InventoryOutboundOrder order, List<InventoryOutboundItem> source) {
        List<Demand> demands = new ArrayList<>();
        Map<Long, Batch> batches = new LinkedHashMap<>();
        Set<Long> materials = new TreeSet<>();
        for (InventoryOutboundItem row : source) if (row.getMaterialId() != null) materials.add(row.getMaterialId());
        for (Long materialId : materials) {
            // 详情查询不加库存行锁；保存及确认时重新锁定并校验。
            for (InventoryStockItem stock : stocks.selectProductionPickCandidatesReadOnly(materialId, order.getWarehouseId(), StockItemStatusEnum.ACTIVE.getValue()))
                batches.put(stock.getItemId(), batch(order, stock));
        }
        Map<Long, Set<Long>> suppliers = new TreeMap<>();
        boolean unknown = false;
        List<String> signature = new ArrayList<>();
        signature.add(order.getOutboundId() + ":" + order.getWarehouseId());
        for (List<InventoryOutboundItem> group : groups(source)) {
            InventoryOutboundItem root = group.getFirst();
            demands.add(new Demand(root.getItemId(), root.getMaterialId(), root.getMaterialCode(), root.getMaterialName(),
                    root.getSpecification(), root.getUnit(), root.getSourceBomItemId(), total(group), coverage(group), group));
            signature.add("d:" + root.getItemId() + ":" + root.getSourceBomItemId() + ":" + decimal(total(group)) + ":" + coverage(group));
            List<String> allocated = new ArrayList<>();
            for (InventoryOutboundItem row : group) {
                if (row.getStockItemId() == null || zero(row.getQuantity()).signum() <= 0) continue;
                Batch batch = batches.get(row.getStockItemId());
                if (batch == null) {
                    InventoryStockItem stock = stocks.selectById(row.getStockItemId());
                    if (stock != null) { batch = batch(order, stock); batches.put(stock.getItemId(), batch); }
                }
                if (batch == null || batch.supplierId() == null) unknown = true;
                else suppliers.computeIfAbsent(row.getMaterialId(), k -> new HashSet<>()).add(batch.supplierId());
                allocated.add(row.getStockItemId() + ":" + decimal(row.getQuantity()) + ":" + row.getBatchNo() + ":" + row.getLocationId() + ":" + row.getRollNo() + ":" + (batch == null ? null : batch.supplierId()));
            }
            Collections.sort(allocated); signature.addAll(allocated);
        }
        return new AllocationView(order.getOutboundId(), order.getOrderStatus(), digest(String.join("|", signature)),
                suppliers.values().stream().anyMatch(s -> s.size() > 1), unknown, demands, new ArrayList<>(batches.values()));
    }

    private Batch batch(InventoryOutboundOrder order, InventoryStockItem stock) {
        Supplier supplier = supplier(stock.getItemId());
        InventoryStorageLocation location = stock.getLocationId() == null ? null : jdbc.query(
                "SELECT location_name FROM inventory_storage_location WHERE location_id=?", rs -> {
                    if (!rs.next()) return null;
                    InventoryStorageLocation l = new InventoryStorageLocation(); l.setLocationName(rs.getString(1)); return l;
                }, stock.getLocationId());
        return new Batch(stock.getItemId(), stock.getMaterialId(), stock.getInventoryItemId(), stock.getWarehouseId(), stock.getLocationId(),
                location == null ? null : location.getLocationName(), stock.getBatchNo(), stock.getRollNo(), stock.getRollWidthMm(), stock.getOriginalRollLengthM(),
                stock.getQuantity(), available(order, stock), supplier.id(), supplier.name());
    }

    protected Supplier supplier(Long stockId) {
        List<Supplier> found = jdbc.query("""
                SELECT o.supplier_id,o.supplier_name
                FROM inventory_stock_item s
                LEFT JOIN inventory_iqc_batch q ON q.batch_id=s.iqc_batch_id
                LEFT JOIN inventory_inbound_item i ON i.item_id=COALESCE(s.source_inbound_item_id,q.source_inbound_item_id)
                LEFT JOIN inventory_inbound_order o ON o.inbound_id=i.inbound_id
                WHERE s.item_id=?
                """, (rs, index) -> new Supplier(rs.getObject("supplier_id", Long.class), rs.getString("supplier_name")), stockId);
        return found.isEmpty() ? new Supplier(null, null) : found.getFirst();
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String decimal(BigDecimal value) { return zero(value).stripTrailingZeros().toPlainString(); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
