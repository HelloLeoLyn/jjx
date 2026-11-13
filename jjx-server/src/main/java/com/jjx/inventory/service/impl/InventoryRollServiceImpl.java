package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.save.RollReturnSaveDTO;
import com.jjx.inventory.dto.save.RollReceiptPlanSaveDTO.RollEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.inventory.enums.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryRollService;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InventoryRollServiceImpl implements InventoryRollService {
    private static final ObjectMapper PLAN_JSON = new ObjectMapper();
    public static final String RETURN_SOURCE = "roll_return";
    public static final String REVERSAL_SOURCE = "roll_reversal";
    private final InventoryStockItemMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryInboundOrderMapper inboundMapper;
    private final InventoryInboundItemMapper inboundItemMapper;
    private final InventoryOutboundOrderMapper outboundMapper;
    private final InventoryOutboundItemMapper outboundItemMapper;
    private final InventoryItemMapper itemMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryStorageLocationMapper locationMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockMutationService mutation;
    private final RedisSequenceService sequence;

    @Override
    public BigDecimal area(BigDecimal widthMm, BigDecimal lengthM) {
        positive(widthMm, "卷宽"); positive(lengthM, "卷长");
        if (widthMm.stripTrailingZeros().scale() > 4 || lengthM.stripTrailingZeros().scale() > 4)
            throw new BusinessException("卷宽、卷长最多保留4位小数");
        BigDecimal area = widthMm.multiply(lengthM).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        if (area.signum() <= 0 || area.compareTo(new BigDecimal("99999999.9999")) > 0)
            throw new BusinessException("卷材面积超出库存数量范围");
        return area;
    }

    @Override
    public void normalizeInbound(InventoryInboundItem item, Map<?, ?> input) {
        boolean supplied = !Objects.toString(input.get("rollNo"), "").isBlank() || input.get("rollWidthMm") != null || input.get("rollLengthM") != null;
        if (!supplied) return;
        String roll = Objects.toString(input.get("rollNo"), "").trim();
        if (roll.isBlank() || roll.length() > 64) throw new BusinessException("请填写不超过64字的卷号");
        item.setRollNo(roll);
        item.setRollWidthMm(decimal(input.get("rollWidthMm")));
        item.setRollLengthM(decimal(input.get("rollLengthM")));
        item.setQuantity(area(item.getRollWidthMm(), item.getRollLengthM()));
        validateWidth(item.getMaterialId(), item.getRollWidthMm());
        validateIdentity(item.getInventoryItemId(), item.getMaterialId(), item.getMaterialCode(), item.getUnit());
        if (item.getBatchNo() == null || item.getBatchNo().isBlank()) throw new BusinessException("卷号不能替代批次号，请填写来料批次");
    }

    @Override
    public InventoryStockItem initialStock(InventoryInboundOrder order, InventoryInboundItem item, BigDecimal delta) {
        validateIdentity(item.getInventoryItemId(), item.getMaterialId(), item.getMaterialCode(), item.getUnit());
        if (item.getItemId() == null || item.getRollNo() == null || item.getRollNo().isBlank()
                || item.getRollNo().length() > 64 || item.getBatchNo() == null || item.getBatchNo().isBlank())
            throw new BusinessException("逐卷入库缺少原始明细、卷号或来料批次");
        validatePosition(order.getWarehouseId(), item.getLocationId());
        if (item.getSourceOutboundItemId() != null || item.getReversalOfItemId() != null)
            throw new BusinessException("余卷退料和红冲请使用专用入口");
        validateWidth(item.getMaterialId(), item.getRollWidthMm());
        BigDecimal measured = area(item.getRollWidthMm(), item.getRollLengthM());
        if (measured.compareTo(delta) != 0 || zero(item.getPostedQuantity()).signum() != 0)
            throw new BusinessException("逐卷入库必须整卷允收过账，部分合格请先按实际物理卷拆分收货明细");
        if (!stockMapper.selectRollForUpdate(item.getRollNo()).isEmpty()) throw new BusinessException("卷号已存在，不可重复入库");
        InventoryStockItem stock = new InventoryStockItem();
        stock.setInventoryItemId(item.getInventoryItemId()); stock.setMaterialId(item.getMaterialId());
        stock.setMaterialCode(item.getMaterialCode()); stock.setMaterialName(item.getMaterialName());
        stock.setWarehouseId(order.getWarehouseId()); stock.setLocationId(item.getLocationId());
        stock.setBatchNo(item.getBatchNo()); stock.setIqcBatchId(item.getIqcBatchId());
        stock.setProductionDate(item.getProductionDate()); stock.setExpiryDate(item.getExpiryDate());
        stock.setRollNo(item.getRollNo()); stock.setRollWidthMm(item.getRollWidthMm());
        stock.setOriginalRollLengthM(item.getRollLengthM()); stock.setSourceInboundItemId(item.getItemId());
        stock.setQuantity(BigDecimal.ZERO); stock.setReservedQuantity(BigDecimal.ZERO); stock.setUnitCost(item.getUnitPrice());
        stock.setStatus(StockItemStatusEnum.ACTIVE.getValue());
        return stock;
    }

    @Override
    public String normalizeReceiptPlan(InventoryInboundItem item, List<RollEntry> entries, BigDecimal target) {
        validateIdentity(item.getInventoryItemId(), item.getMaterialId(), item.getMaterialCode(), item.getUnit());
        positive(target, "本次允许入库面积");
        if (item.getRollNo() != null) throw new BusinessException("原明细已指定单卷，不能再挂多卷计划");
        if (entries == null || entries.isEmpty() || entries.size() > 500) throw new BusinessException("请录入1至500卷");
        var seen = new HashSet<String>();
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> facts = new ArrayList<>();
        for (RollEntry entry : entries) {
            String rollNo = entry.getRollNo() == null ? "" : entry.getRollNo().trim();
            if (rollNo.isBlank() || rollNo.length() > 64 || !seen.add(rollNo.toLowerCase(Locale.ROOT)))
                throw new BusinessException("卷号不能为空、超过64字或重复");
            BigDecimal measured = area(entry.getWidthMm(), entry.getLengthM());
            validateWidth(item.getMaterialId(), entry.getWidthMm());
            facts.add(Map.of("rollNo", rollNo, "widthMm", entry.getWidthMm(), "lengthM", entry.getLengthM()));
            total = total.add(measured);
        }
        areaToleranceRemark(total, target);
        for (String rollNo : facts.stream().map(f -> f.get("rollNo").toString()).sorted(String.CASE_INSENSITIVE_ORDER).toList())
            if (!stockMapper.selectRollForUpdate(rollNo).isEmpty()) throw new BusinessException("卷号 " + rollNo + " 已存在");
        try { return PLAN_JSON.writeValueAsString(facts); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("卷计划保存格式错误"); }
    }

    /** 边界含±5%，比较未舍入的面积差；差异率仅显示时舍入。 */
    public static String areaToleranceRemark(BigDecimal measured, BigDecimal allowed) {
        if (measured == null || measured.signum() <= 0 || allowed == null || allowed.signum() <= 0)
            throw new BusinessException("实测面积和允许面积必须大于0");
        BigDecimal difference = measured.subtract(allowed);
        String trace = "实测面积=" + measured.stripTrailingZeros().toPlainString() + "㎡/允许面积="
                + allowed.stripTrailingZeros().toPlainString() + "㎡/差异率="
                + difference.multiply(BigDecimal.valueOf(100)).divide(allowed, 4, RoundingMode.HALF_UP).toPlainString() + "%";
        if (difference.abs().compareTo(allowed.multiply(new BigDecimal("0.05"))) > 0)
            throw new BusinessException(trace + "，超出±5%容差，请改单或走审批后再入库");
        return trace;
    }

    public static BigDecimal receiptAmount(InventoryInboundItem item, BigDecimal allowed) {
        if (item.getAmount() != null && item.getQuantity() != null && item.getQuantity().signum() > 0)
            return item.getAmount().multiply(allowed).divide(item.getQuantity(), 2, RoundingMode.HALF_UP);
        return item.getUnitPrice() == null ? null : item.getUnitPrice().multiply(allowed).setScale(2, RoundingMode.HALF_UP);
    }

    /** 仅重算本次允收份额的实测成本，不改采购金额、原收货量或IQC允收量。 */
    public static BigDecimal measuredUnitCost(InventoryInboundItem item, BigDecimal allowed, BigDecimal measured) {
        BigDecimal amount = item.getAmount();
        if (amount != null && item.getQuantity() != null && item.getQuantity().signum() > 0)
            return amount.multiply(allowed).divide(item.getQuantity().multiply(measured), 4, RoundingMode.HALF_UP);
        return item.getUnitPrice() == null ? null : item.getUnitPrice().multiply(allowed).divide(measured, 4, RoundingMode.HALF_UP);
    }

    @Override
    public List<RollEntry> readReceiptPlan(InventoryInboundItem item) {
        if (item.getRollPlanJson() == null || item.getRollPlanJson().isBlank()) return List.of();
        try {
            List<RollEntry> entries = PLAN_JSON.readValue(item.getRollPlanJson(), new TypeReference<List<RollEntry>>() {});
            for (RollEntry entry : entries) entry.setArea(area(entry.getWidthMm(), entry.getLengthM()));
            return entries;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("原入库明细卷计划损坏，请核实"); }
    }

    @Override
    public List<InventoryStockItem> receiptStocks(InventoryInboundOrder order, InventoryInboundItem item, BigDecimal delta) {
        if (item.getRollPlanJson() == null || item.getRollPlanJson().isBlank()) {
            BigDecimal measured = area(item.getRollWidthMm(), item.getRollLengthM());
            if ("PURCHASE".equalsIgnoreCase(order.getSourceType()) || "PURCHASE_ORDER".equalsIgnoreCase(order.getSourceType())) {
                areaToleranceRemark(measured, delta);
                InventoryInboundItem physical = new InventoryInboundItem();
                org.springframework.beans.BeanUtils.copyProperties(item, physical);
                physical.setUnitPrice(measuredUnitCost(item, delta, measured));
                return List.of(initialStock(order, physical, measured));
            }
            return List.of(initialStock(order, item, delta));
        }
        if (zero(item.getPostedQuantity()).signum() != 0) throw new BusinessException("已过账的卷计划不可再次入库");
        List<RollEntry> entries = readReceiptPlan(item);
        // 重新验证当前允许量，避免保存计划后IQC允收发生变化；锁序按卷号固定。
        normalizeReceiptPlan(item, entries, delta);
        BigDecimal measuredTotal = entries.stream().map(RollEntry::getArea).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal measuredCost = measuredUnitCost(item, delta, measuredTotal);
        entries.sort(Comparator.comparing(RollEntry::getRollNo, String.CASE_INSENSITIVE_ORDER));
        List<InventoryStockItem> stocks = new ArrayList<>();
        for (RollEntry entry : entries) {
            InventoryInboundItem physical = new InventoryInboundItem();
            org.springframework.beans.BeanUtils.copyProperties(item, physical);
            physical.setRollNo(entry.getRollNo()); physical.setRollWidthMm(entry.getWidthMm());
            physical.setRollLengthM(entry.getLengthM());
            physical.setUnitPrice(measuredCost);
            stocks.add(initialStock(order, physical, entry.getArea()));
        }
        return stocks;
    }

    @Override
    public void normalizeOutbound(InventoryOutboundOrder order, InventoryOutboundItem item, Map<?, ?> input) {
        if (input.get("stockItemId") == null && input.get("rollNo") == null) return;
        if (input.get("stockItemId") == null) throw new BusinessException("请从库存明细选择具体卷材");
        item.setStockItemId(Long.valueOf(input.get("stockItemId").toString()));
        item.setRollNo(Objects.toString(input.get("rollNo"), null));
        InventoryStockItem stock = issueStock(order, item);
        if (stock.getRollNo() == null) throw new BusinessException("所选库存明细不是卷材");
        copyIssueIdentity(item, stock);
    }

    @Override
    public InventoryStockItem issueStock(InventoryOutboundOrder order, InventoryOutboundItem item) {
        if ("work_order".equalsIgnoreCase(order.getSourceType()) && item.getSourceBomItemId() == null)
            throw new BusinessException("工单整卷领料必须关联受控BOM明细，不能用普通出库单绕过需求/超领校验；包装覆盖由生产领料流程接入");
        InventoryStockItem selected = stockMapper.selectById(item.getStockItemId());
        if (selected == null || selected.getRollNo() == null) throw new BusinessException("所选卷材不存在");
        List<InventoryStockItem> rows = stockMapper.selectRollForUpdate(selected.getRollNo());
        InventoryStockItem stock = rows.stream().filter(s -> s.getItemId().equals(item.getStockItemId())).findFirst()
                .orElseThrow(() -> new BusinessException("卷材位置已变化，请重新选择"));
        validateIdentity(stock.getInventoryItemId(), stock.getMaterialId(), stock.getMaterialCode(), item.getUnit());
        if (!Objects.equals(order.getWarehouseId(), stock.getWarehouseId())
                || !StockItemStatusEnum.ACTIVE.getValue().equals(stock.getStatus())
                || (item.getInventoryItemId() != null && !Objects.equals(item.getInventoryItemId(), stock.getInventoryItemId()))
                || (item.getMaterialId() != null && !Objects.equals(item.getMaterialId(), stock.getMaterialId()))
                || (item.getRollNo() != null && !Objects.equals(item.getRollNo(), stock.getRollNo()))
                || (item.getLocationId() != null && !Objects.equals(item.getLocationId(), stock.getLocationId())))
            throw new BusinessException("卷材物料、仓库、库位或卷号与单据不一致");
        validateWidth(stock.getMaterialId(), stock.getRollWidthMm());
        positive(stock.getQuantity(), "卷材在库面积");
        if (stock.getUnitCost() == null) throw new BusinessException("原卷过账成本缺失，请先核实");
        if (zero(stock.getReservedQuantity()).signum() != 0) throw new BusinessException("该卷已被预留，请先释放对应预留");
        if (item.getQuantity() == null || stock.getQuantity().compareTo(item.getQuantity()) != 0)
            throw new BusinessException("卷材必须整卷发出，实发面积应为 " + stock.getQuantity().toPlainString() + "㎡");
        copyIssueIdentity(item, stock);
        return stock;
    }

    private void copyIssueIdentity(InventoryOutboundItem item, InventoryStockItem stock) {
        item.setStockItemId(stock.getItemId()); item.setRollNo(stock.getRollNo());
        item.setInventoryItemId(stock.getInventoryItemId()); item.setMaterialId(stock.getMaterialId());
        item.setMaterialCode(stock.getMaterialCode()); item.setMaterialName(stock.getMaterialName());
        item.setBatchNo(stock.getBatchNo()); item.setLocationId(stock.getLocationId());
        item.setUnitPrice(stock.getUnitCost()); item.setAmount(amount(item.getQuantity(), stock.getUnitCost()));
    }

    @Override
    public Long returnRoll(RollReturnSaveDTO input) {
        InventoryOutboundItem issued = lockIssue(input.getSourceOutboundItemId());
        if (!Objects.equals(issued.getRollNo(), input.getRollNo())) throw new BusinessException("退料卷号与原出库卷号不一致");
        List<InventoryStockItem> rows = stockMapper.selectRollForUpdate(issued.getRollNo());
        List<InventoryTransaction> flows = transactionMapper.selectRollForUpdate(issued.getRollNo());
        InventoryTransaction issue = originalIssue(issued, flows);
        InventoryTransaction latest = flows.stream().filter(t -> isOutbound(t) && t.getQuantity().signum() < 0)
                .max(Comparator.comparing(InventoryTransaction::getTransactionId)).orElseThrow();
        if (!Objects.equals(latest.getSourceItemId(), issued.getItemId())) throw new BusinessException("该卷已再次发出，请关联最近一次出库明细");
        if (rows.stream().anyMatch(s -> zero(s.getQuantity()).signum() > 0)) throw new BusinessException("该物理卷已在库，不可重复退料");
        BigDecimal netReturned = netReturned(issued.getItemId());
        if (netReturned.signum() != 0) throw new BusinessException("该次发料已有退料记录；纠正长度请先红冲原退料");
        InventoryStockItem origin = rows.stream().filter(s -> s.getItemId().equals(issue.getStockItemId())).findFirst()
                .orElseThrow(() -> new BusinessException("原卷库存追溯缺失"));
        BigDecimal quantity = area(origin.getRollWidthMm(), input.getReturnedLengthM());
        if (quantity.compareTo(issue.getQuantity().negate().subtract(netReturned)) > 0)
            throw new BusinessException("退料面积不能超过原实发净额");
        validatePosition(input.getWarehouseId(), input.getLocationId());
        InventoryStockItem target = position(rows, origin, input.getWarehouseId(), input.getLocationId());
        target.setUnitCost(issue.getUnitCost());
        InventoryInboundOrder order = returnHeader(issued.getOutboundId(), quantity, issue.getUnitCost(), input.getWarehouseId(), input.getReason());
        inboundMapper.insert(order);
        InventoryInboundItem returned = returnItem(issued, origin, order.getInboundId(), quantity, issue.getUnitCost());
        returned.setRollLengthM(input.getReturnedLengthM()); returned.setLocationId(input.getLocationId());
        inboundItemMapper.insert(returned);
        InventoryTransaction tx = inboundFlow(order, returned, input.getReason());
        InventoryStockItem saved = mutation.applyDelta(target, quantity, tx);
        returned.setStockItemId(saved.getItemId()); inboundItemMapper.updateById(returned);
        return order.getInboundId();
    }

    @Override
    public Long reverseReturn(Long itemId, String reason) {
        requireReason(reason);
        InventoryInboundItem peek = inboundItemMapper.selectById(itemId);
        if (peek == null || peek.getSourceOutboundItemId() == null || peek.getReversalOfItemId() != null)
            throw new BusinessException("只能红冲原始余卷退料明细");
        InventoryOutboundItem issued = lockIssue(peek.getSourceOutboundItemId());
        InventoryInboundOrder original = inboundMapper.selectByIdForUpdate(peek.getInboundId());
        InventoryInboundItem item = inboundItemMapper.selectByIdForUpdate(itemId);
        requireCompleted(original.getOrderStatus());
        List<InventoryStockItem> rows = stockMapper.selectRollForUpdate(item.getRollNo());
        if (inboundItemMapper.selectCount(new LambdaQueryWrapper<InventoryInboundItem>().eq(InventoryInboundItem::getReversalOfItemId, itemId)) > 0)
            throw new BusinessException("该退料已红冲");
        InventoryStockItem stock = rows.stream().filter(s -> Objects.equals(s.getItemId(), item.getStockItemId())).findFirst().orElseThrow();
        if (stock.getQuantity().compareTo(item.getQuantity()) != 0 || zero(stock.getReservedQuantity()).signum() != 0)
            throw new BusinessException("退回卷材已发出或被预留，不能红冲");
        InventoryInboundOrder order = returnHeader(issued.getOutboundId(), item.getQuantity().negate(), item.getUnitPrice(), stock.getWarehouseId(), reason);
        order.setSourceType(REVERSAL_SOURCE); order.setSourceId(item.getInboundId()); order.setSourceNo(original.getInboundNo());
        inboundMapper.insert(order);
        InventoryInboundItem reverse = returnItem(issued, stock, order.getInboundId(), item.getQuantity().negate(), item.getUnitPrice());
        reverse.setReversalOfItemId(itemId); reverse.setStockItemId(stock.getItemId());
        reverse.setRollLengthM(item.getRollLengthM().negate()); inboundItemMapper.insert(reverse);
        mutation.applyDelta(stock, reverse.getQuantity(), inboundFlow(order, reverse, reason));
        return order.getInboundId();
    }

    @Override
    public Long reverseIssue(Long itemId, String reason) {
        requireReason(reason);
        InventoryOutboundItem item = lockIssue(itemId);
        InventoryOutboundOrder original = outboundMapper.selectById(item.getOutboundId());
        List<InventoryStockItem> rows = stockMapper.selectRollForUpdate(item.getRollNo());
        List<InventoryTransaction> flows = transactionMapper.selectRollForUpdate(item.getRollNo());
        InventoryTransaction issue = originalIssue(item, flows);
        if (netReturned(itemId).signum() != 0) throw new BusinessException("请先红冲该卷的退料，再红冲原发料");
        if (outboundItemMapper.selectCount(new LambdaQueryWrapper<InventoryOutboundItem>().eq(InventoryOutboundItem::getReversalOfItemId, itemId)) > 0)
            throw new BusinessException("该发料已红冲");
        // 退料及其红冲允许在原发料后出现；新的发料则不能回冲旧单。
        if (flows.stream().anyMatch(t -> isOutbound(t) && t.getQuantity().signum() < 0 && t.getTransactionId() > issue.getTransactionId()))
            throw new BusinessException("该卷已有后续发料，不能红冲旧出库");
        if (rows.stream().anyMatch(s -> zero(s.getQuantity()).signum() > 0)) throw new BusinessException("该卷已在库，不能重复恢复");
        InventoryStockItem stock = rows.stream().filter(s -> Objects.equals(s.getItemId(), issue.getStockItemId())).findFirst().orElseThrow();
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo(sequence.generateBusinessNumberByType("outbound_manual", "OUT", "yyMMdd", 3));
        order.setOutboundType(original.getOutboundType()); order.setSourceType(REVERSAL_SOURCE);
        order.setSourceId(original.getOutboundId()); order.setSourceNo(original.getOutboundNo());
        order.setWarehouseId(stock.getWarehouseId()); order.setOutboundDate(LocalDate.now());
        order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());
        order.setTotalQuantity(item.getQuantity().negate()); order.setTotalAmount(amount(order.getTotalQuantity(), issue.getUnitCost()));
        outboundMapper.insert(order);
        InventoryOutboundItem reverse = new InventoryOutboundItem();
        org.springframework.beans.BeanUtils.copyProperties(item, reverse);
        reverse.setItemId(null); reverse.setOutboundId(order.getOutboundId()); reverse.setReversalOfItemId(itemId);
        reverse.setQuantity(item.getQuantity().negate()); reverse.setUnitPrice(issue.getUnitCost());
        reverse.setAmount(amount(reverse.getQuantity(), issue.getUnitCost())); reverse.setRemark(reason);
        outboundItemMapper.insert(reverse);
        InventoryTransaction tx = new InventoryTransaction();
        tx.setTransactionType(TransactionTypeEnum.OUTBOUND.getCode()); tx.setSourceType(REVERSAL_SOURCE);
        tx.setSourceId(order.getOutboundId()); tx.setSourceNo(order.getOutboundNo()); tx.setSourceItemId(reverse.getItemId());
        tx.setUnitCost(issue.getUnitCost()); actor(tx, reason);
        mutation.applyDelta(stock, item.getQuantity(), tx);
        return order.getOutboundId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransaction> flow(String rollNo) {
        return transactionMapper.selectList(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getRollNo, rollNo).orderByAsc(InventoryTransaction::getTransactionId));
    }

    private InventoryOutboundItem lockIssue(Long id) {
        InventoryOutboundItem peek = outboundItemMapper.selectById(id);
        if (peek == null) throw new BusinessException("原出库明细不存在");
        InventoryOutboundOrder order = outboundMapper.selectByIdForUpdate(peek.getOutboundId());
        requireCompleted(order.getOrderStatus());
        InventoryOutboundItem item = outboundItemMapper.selectByIdForUpdate(id);
        if (item.getRollNo() == null || item.getReversalOfItemId() != null || zero(item.getQuantity()).signum() <= 0)
            throw new BusinessException("请选择已过账的原始卷材出库明细");
        return item;
    }

    private InventoryTransaction originalIssue(InventoryOutboundItem item, List<InventoryTransaction> flows) {
        List<InventoryTransaction> matches = flows.stream().filter(t -> isOutbound(t) && Objects.equals(t.getSourceItemId(), item.getItemId())
                && Objects.equals(t.getSourceId(), item.getOutboundId()) && t.getQuantity().signum() < 0).toList();
        if (matches.size() != 1) throw new BusinessException("原实发流水不完整，不能退料或红冲");
        InventoryTransaction tx = matches.get(0);
        if (tx.getQuantity().negate().compareTo(item.getQuantity()) != 0 || tx.getUnitCost() == null)
            throw new BusinessException("原实发数量或过账成本缺失");
        if (outboundItemMapper.selectCount(new LambdaQueryWrapper<InventoryOutboundItem>().eq(InventoryOutboundItem::getReversalOfItemId, item.getItemId())) > 0)
            throw new BusinessException("原发料已红冲，不能再退料");
        return tx;
    }

    private BigDecimal netReturned(Long sourceId) {
        return inboundItemMapper.selectList(new LambdaQueryWrapper<InventoryInboundItem>()
                .eq(InventoryInboundItem::getSourceOutboundItemId, sourceId)).stream()
                .map(i -> zero(i.getPostedQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private InventoryStockItem position(List<InventoryStockItem> rows, InventoryStockItem origin, Long warehouse, Long location) {
        for (InventoryStockItem s : rows) if (Objects.equals(s.getWarehouseId(), warehouse) && Objects.equals(s.getLocationId(), location)) return s;
        InventoryStockItem target = new InventoryStockItem();
        org.springframework.beans.BeanUtils.copyProperties(origin, target);
        target.setItemId(null); target.setWarehouseId(warehouse); target.setLocationId(location);
        target.setQuantity(BigDecimal.ZERO); target.setReservedQuantity(BigDecimal.ZERO);
        return target;
    }

    private InventoryInboundOrder returnHeader(Long source, BigDecimal qty, BigDecimal cost, Long warehouse, String reason) {
        requireReason(reason);
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo(sequence.generateBusinessNumberByType("inbound", "IN", "yyMMdd", 3));
        order.setInboundType(InboundTypeEnum.ROLL_RETURN.getCode()); order.setSourceType(RETURN_SOURCE); order.setSourceId(source);
        InventoryOutboundOrder original = outboundMapper.selectById(source);
        order.setSourceNo(original.getOutboundNo()); order.setWarehouseId(warehouse); order.setInboundDate(LocalDate.now());
        order.setTotalQuantity(qty); order.setTotalAmount(amount(qty, cost));
        order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue()); order.setRemark(reason);
        return order;
    }

    private InventoryInboundItem returnItem(InventoryOutboundItem issued, InventoryStockItem origin, Long inboundId, BigDecimal qty, BigDecimal cost) {
        InventoryInboundItem item = new InventoryInboundItem();
        item.setInboundId(inboundId); item.setInventoryItemId(origin.getInventoryItemId());
        item.setMaterialId(origin.getMaterialId()); item.setMaterialCode(origin.getMaterialCode()); item.setMaterialName(origin.getMaterialName());
        item.setSpecification(issued.getSpecification()); item.setUnit(issued.getUnit()); item.setRollNo(origin.getRollNo());
        item.setRollWidthMm(origin.getRollWidthMm()); item.setSourceOutboundItemId(issued.getItemId());
        item.setQuantity(qty); item.setPostedQuantity(qty); item.setUnitPrice(cost); item.setAmount(amount(qty, cost));
        item.setBatchNo(origin.getBatchNo()); item.setIqcBatchId(origin.getIqcBatchId()); item.setLocationId(origin.getLocationId());
        item.setSortOrder(1);
        return item;
    }

    private InventoryTransaction inboundFlow(InventoryInboundOrder order, InventoryInboundItem item, String reason) {
        InventoryTransaction tx = new InventoryTransaction(); tx.setTransactionType(TransactionTypeEnum.INBOUND.getCode());
        tx.setSourceType(order.getSourceType()); tx.setSourceId(order.getInboundId()); tx.setSourceNo(order.getInboundNo());
        tx.setSourceItemId(item.getItemId()); tx.setUnitCost(item.getUnitPrice()); actor(tx, reason); return tx;
    }

    private void actor(InventoryTransaction tx, String reason) {
        tx.setOperatorId(SecurityUtils.getUserId()); tx.setOperatorName(SecurityUtils.getDisplayName()); tx.setRemark(reason);
    }
    private void validateIdentity(Long id, Long materialId, String code, String suppliedUnit) {
        InventoryMaterial material = materialId == null ? null : materialMapper.selectById(materialId);
        if (material == null || !Objects.equals(code, material.getMaterialCode()) || !squareMetre(material.getUnit())
                || (suppliedUnit != null && !squareMetre(suppliedUnit)))
            throw new BusinessException("卷材必须使用匹配的物料及㎡库存单位，请先核对物料档案");
        if (id != null) {
            InventoryItem item = itemMapper.selectById(id);
            if (item == null || !InventoryItemTypeEnum.MATERIAL.getCode().equals(item.getItemType())
                    || !Objects.equals(item.getSourceId(), materialId) || !Objects.equals(item.getItemCode(), code) || !squareMetre(item.getUnit()))
                throw new BusinessException("库存物品身份与物料档案不一致，请先核实，不可自动改旧账身份");
        }
    }
    private void validateWidth(Long materialId, BigDecimal width) {
        InventoryMaterial material = materialId == null ? null : materialMapper.selectById(materialId);
        if (material != null && material.getWidthMm() != null && width != null && material.getWidthMm().compareTo(width) != 0)
            throw new BusinessException("实收卷宽与物料规格不一致，请选择对应料号");
    }
    private boolean squareMetre(String unit) { return unit != null && Set.of("㎡", "m²", "m2", "M2", "平方米", "平方").contains(unit.trim()); }
    private void validatePosition(Long warehouse, Long location) {
        if (warehouse == null || warehouseMapper.selectById(warehouse) == null) throw new BusinessException("退入仓库不存在");
        if (location != null) {
            InventoryStorageLocation found = locationMapper.selectById(location);
            if (found == null || !Objects.equals(found.getWarehouseId(), warehouse)) throw new BusinessException("库位不属于所选仓库");
        }
    }
    private boolean isOutbound(InventoryTransaction tx) { return TransactionTypeEnum.OUTBOUND.getCode().equalsIgnoreCase(tx.getTransactionType()); }
    private void requireCompleted(Integer status) { if (!InventoryOrderStatusEnum.COMPLETED.getValue().equals(status)) throw new BusinessException("原单据尚未完成过账"); }
    private void requireReason(String reason) { if (reason == null || reason.isBlank() || reason.length() > 200) throw new BusinessException("请填写不超过200字的原因"); }
    private BigDecimal decimal(Object value) { try { return value == null ? null : new BigDecimal(value.toString()); } catch (NumberFormatException e) { throw new BusinessException("卷材尺寸格式错误"); } }
    private void positive(BigDecimal value, String name) { if (value == null || value.signum() <= 0) throw new BusinessException(name + "必须大于0"); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal amount(BigDecimal qty, BigDecimal cost) { return cost == null ? null : qty.multiply(cost).setScale(2, RoundingMode.HALF_UP); }
}
