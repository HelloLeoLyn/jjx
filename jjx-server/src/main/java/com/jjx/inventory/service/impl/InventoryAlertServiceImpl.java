package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryAlertLog;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.dto.query.AlertQueryDTO;
import com.jjx.inventory.dto.vo.AlertVO;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;

/**
 * 库存预警服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAlertServiceImpl extends ServiceImpl<InventoryAlertLogMapper, InventoryAlertLog>
        implements InventoryAlertService {

    private final InventoryAlertLogMapper alertLogMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final com.jjx.inventory.mapper.InventoryItemMapper inventoryItemMapper;
    private final com.jjx.inventory.mapper.OrderMaterialReserveMapper orderMaterialReserveMapper;
    private final EventPublisher eventPublisher;
    private final OrderMapper orderMapper;
    private final SalesOrderProductMapper orderProductMapper;
    private final EngineeringBomMapper bomMapper;
    private final EngineeringBomItemMapper bomItemMapper;
    private final com.jjx.purchase.mapper.PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    private final com.jjx.inventory.mapper.SalesOrderStockReserveMapper salesOrderStockReserveMapper;

    /**
     * 库存主数据/预警事件统一发布（2026-09-21 dev-20260921-013 库存批 3/3）：
     * 手写 payload，bizNo 取对象编码（删除类由调用方传入删除前取到的编码）。
     */
    private void publishAlertEvent(String eventCode, Long id, String knownCode) {
        InventoryAlertLog m = (id == null || knownCode != null) ? null : alertLogMapper.selectById(id);
        String code = knownCode != null ? knownCode : (m == null ? null : m.getMaterialCode());
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "inventory_alert", id, code);
        // 删除路径由调用方传 knownCode，此时 m 为 null —— 不防空会 NPE（2026-09-21 dev-20260921-023）
        if (m != null) {
            payload.put("materialName", m.getMaterialName());
            payload.put("alertType", m.getAlertType());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

    @Override
    public IPage<AlertVO> page(AlertQueryDTO query) {
        LambdaQueryWrapper<InventoryAlertLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getAlertType() != null && !query.getAlertType().isEmpty()) wrapper.eq(InventoryAlertLog::getAlertType, query.getAlertType());
        if (query.getAlertLevel() != null && !query.getAlertLevel().isEmpty()) wrapper.eq(InventoryAlertLog::getAlertLevel, query.getAlertLevel());
        if (query.getStatus() != null && !query.getStatus().isEmpty()) wrapper.eq(InventoryAlertLog::getStatus, query.getStatus());
        if (query.getAlertTimeStart() != null) wrapper.ge(InventoryAlertLog::getAlertTime, query.getAlertTimeStart());
        if (query.getAlertTimeEnd() != null) wrapper.le(InventoryAlertLog::getAlertTime, query.getAlertTimeEnd());
        if (query.getMaterialId() != null) wrapper.eq(InventoryAlertLog::getMaterialId, query.getMaterialId());
        wrapper.orderByDesc(InventoryAlertLog::getAlertTime);

        Page<InventoryAlertLog> logPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryAlertLog> logResult = alertLogMapper.selectPage(logPage, wrapper);
        Page<AlertVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(logResult.getTotal());
        voPage.setPages(logResult.getPages());
        voPage.setRecords(convertToVOList(logResult.getRecords()));
        return voPage;
    }

    @Override
    public void executeAlertCheck() {
        log.info("开始执行库存预警检查");
        checkSafeStockAlert();
        checkMaxStockAlert();
        checkExpiryAlert();
        checkObsoleteAlert();
        log.info("库存预警检查完成");
    }

    @Override
    public void checkOrderShortage(Long orderId) {
        // 兼容调用（发送确认/客户确认自动触发）：忽略明细
        checkOrderShortageWithDetail(orderId);
    }

    @Override
    public Map<String, Object> previewOrderShortage(Long orderId) {
        return calculateOrderShortage(orderId);
    }

    private Map<String, Object> calculateOrderShortage(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> productRows = new ArrayList<>();
        List<Map<String, Object>> materialRows = new ArrayList<>();
        result.put("orderNo", order == null ? null : order.getOrderNo());
        result.put("productRows", productRows);
        result.put("materialRows", materialRows);
        if (order == null) {
            result.put("summary", createShortageSummary(0, 0, 0, 0));
            return result;
        }
        String orderNo = order.getOrderNo();
        List<SalesOrderProduct> products = orderProductMapper.selectList(
                new LambdaQueryWrapper<SalesOrderProduct>()
                        .eq(SalesOrderProduct::getOrderId, orderId));
        if (products == null || products.isEmpty()) {
            result.put("summary", createShortageSummary(0, 0, 0, 0));
            return result;
        }

        Map<Long, BigDecimal> demandMap = new java.util.HashMap<>();
        Map<Long, String> codeMap = new java.util.HashMap<>();
        Map<Long, String> nameMap = new java.util.HashMap<>();
        int noBomCount = 0;
        int stockCoveredCount = 0; // 产品现货直接覆盖的明细数
        // dev-20260930-019（P2）：本单占用台账（产品 → [0]=实预留 [1]=待生产），供预览展示"占了多少/还差多少"
        Map<Long, BigDecimal[]> occupancy = new java.util.HashMap<>();
        try {
            List<Map<String, Object>> occRows = salesOrderStockReserveMapper.sumActiveByProductAndType(orderId);
            if (occRows != null) {
                for (Map<String, Object> r : occRows) {
                    if (r.get("productId") == null) continue;
                    long pid = ((Number) r.get("productId")).longValue();
                    int type = r.get("reserveType") == null ? 1 : ((Number) r.get("reserveType")).intValue();
                    BigDecimal q = toDecimal(r.get("qty"));
                    BigDecimal[] arr = occupancy.computeIfAbsent(pid, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    if (type == 2) {
                        arr[1] = arr[1].add(q);
                    } else {
                        arr[0] = arr[0].add(q);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询订单占用台账失败(展示按0处理): orderId={}, err={}", orderId, e.getMessage());
        }
        for (SalesOrderProduct p : products) {
            if (p.getProductId() == null) {
                log.info("订单{}明细产品ID为空，跳过", orderNo);
                continue;
            }
            BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
            // dev-20260930-023（P4a pegging）：本单该产品的来源在制工单（需求追溯，派生）
            String wipWoNo = productWipWorkOrderNos(orderId, p.getProductId());

            // dev-20260930-018（P1 口径解耦）：
            //   现货净可用 = max(0, 现货总量 − 优先级高于本单的有效订单未满足需求)
            //   有效可用   = 现货净可用 + 本单在制未完工（用于判断"是否还需投产"，避免重复排产）
            //   BOM 展开缺口 = max(0, 需求 − 现货净可用)（不含在制——已开工的部分照样要料）
            BigDecimal productAvailable = productNetSpotAvailable(p.getProductId(), orderId);
            BigDecimal materialGap = orderQty.subtract(productAvailable);
            if (materialGap.compareTo(BigDecimal.ZERO) <= 0) {
                stockCoveredCount++;
                productRows.add(createProductRow(p, orderQty, productAvailable, BigDecimal.ZERO, "stock-covered", occupancy.get(p.getProductId()), wipWoNo));
                continue;
            }
            BigDecimal productionGap = orderQty.subtract(productAvailable.add(productWipRemaining(orderId, p.getProductId())));
            if (productionGap.compareTo(BigDecimal.ZERO) < 0) {
                productionGap = BigDecimal.ZERO;
            }

            EngineeringBom bom = bomMapper.selectOne(new LambdaQueryWrapper<EngineeringBom>()
                    .eq(EngineeringBom::getProductId, p.getProductId())
                    .eq(EngineeringBom::getIsCurrent, true)
                    .eq(EngineeringBom::getApproveStatus, 3));
            if (bom == null) {
                noBomCount++;
                productRows.add(createProductRow(p, orderQty, productAvailable, productionGap, "no-bom", occupancy.get(p.getProductId()), wipWoNo));
                continue;
            }
            productRows.add(createProductRow(p, orderQty, productAvailable, productionGap,
                    productionGap.compareTo(BigDecimal.ZERO) > 0 ? "to-produce" : "wip-covered", occupancy.get(p.getProductId()), wipWoNo));
            List<EngineeringBomItem> items = bomItemMapper.selectList(
                    new LambdaQueryWrapper<EngineeringBomItem>()
                            .eq(EngineeringBomItem::getBomId, bom.getBomId()));
            for (EngineeringBomItem item : items) {
                if (item.getMaterialId() == null) continue;
                BigDecimal need = batchDemand(item, materialGap);
                demandMap.merge(item.getMaterialId(), need, BigDecimal::add);
                codeMap.putIfAbsent(item.getMaterialId(), item.getMaterialCode());
                nameMap.putIfAbsent(item.getMaterialId(), item.getMaterialName());
            }
        }

        java.util.Map<Long, java.math.BigDecimal> inTransitMap = new java.util.HashMap<>();
        try {
            // dev-20260930-018：在途只算"预计到货日 ≤ 本单交期"（本单无交期则不过滤）
            java.util.List<java.util.Map<String, Object>> transitRows = (order.getDeliveryDate() != null)
                    ? purchaseOrderItemMapper.selectInTransitByMaterialBefore(order.getDeliveryDate())
                    : purchaseOrderItemMapper.selectInTransitByMaterial();
            for (java.util.Map<String, Object> row : transitRows) {
                Object mid = row.get("material_id");
                Object qty = row.get("in_transit");
                if (mid != null && qty != null) {
                    inTransitMap.put(((Number) mid).longValue(), new BigDecimal(qty.toString()));
                }
            }
        } catch (Exception e) {
            log.warn("查询在途采购量失败: {}", e.getMessage());
        }
        // dev-20260930-021（P3）：本单材料缺口 = 跨订单分摊后分给本单的份额
        java.util.Set<Long> allocScope = new java.util.HashSet<>();
        allocScope.add(orderId);
        java.util.Map<Long, BigDecimal> allocatedShortage =
                allocateMaterialShortageByOrder(allocScope).getOrDefault(orderId, java.util.Collections.emptyMap());
        int shortageCount = 0;
        int coveredCount = 0;
        for (Map.Entry<Long, BigDecimal> entry : demandMap.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal demand = entry.getValue();
            InventoryStock stock = stockMapper.selectByMaterialId(materialId);
            BigDecimal available = (stock != null && stock.getAvailableQuantity() != null)
                    ? stock.getAvailableQuantity() : BigDecimal.ZERO;
            // dev-20260930-018（P1）：材料可用量 = 现货可用 − 安全库存(material.safe_stock)；
            // 不再扣"材料预占"(order_material_reserve)——旧账按全额需求生成，与新缺口口径冲突，留待 P2 统一（停扣）
            available = available.subtract(safeStockOf(materialId));
            BigDecimal inTransit = inTransitMap.getOrDefault(materialId, BigDecimal.ZERO);
            // dev-20260930-021（P3）：本单缺口取跨订单分摊后的份额（不再各单各算全量供给）
            BigDecimal actualGap = allocatedShortage.getOrDefault(materialId, BigDecimal.ZERO);
            String status;
            if (actualGap.compareTo(BigDecimal.ZERO) <= 0) {
                if (demand.subtract(available).compareTo(BigDecimal.ZERO) > 0) {
                    coveredCount++;
                    status = "covered";
                } else {
                    status = "ok";
                }
                actualGap = BigDecimal.ZERO;
            } else {
                shortageCount++;
                status = "shortage";
            }
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("materialId", materialId);
            item.put("materialCode", codeMap.get(materialId));
            item.put("materialName", nameMap.get(materialId));
            item.put("demand", demand);
            item.put("available", available);
            item.put("inTransit", inTransit);
            item.put("actualGap", actualGap);
            item.put("status", status);
            materialRows.add(item);
        }
        result.put("summary", createShortageSummary(shortageCount, coveredCount, stockCoveredCount, noBomCount));
        return result;
    }

    private Map<String, Object> createProductRow(SalesOrderProduct product, BigDecimal orderQty,
                                                  BigDecimal productAvailable, BigDecimal needProduce, String status,
                                                  BigDecimal[] occupancy, String wipWorkOrderNo) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("productId", product.getProductId());
        row.put("productCode", product.getProductCode());
        row.put("productName", product.getProductName());
        row.put("orderQty", orderQty);
        row.put("productAvailable", productAvailable);
        row.put("needProduce", needProduce);
        row.put("status", status);
        // dev-20260930-019（P2）：本单占用台账投影——实预留 / 待生产
        BigDecimal reserved = (occupancy == null || occupancy[0] == null) ? BigDecimal.ZERO : occupancy[0];
        BigDecimal pending = (occupancy == null || occupancy[1] == null) ? BigDecimal.ZERO : occupancy[1];
        row.put("reservedQty", reserved);
        row.put("pendingQty", pending);
        // dev-20260930-023（P4a pegging）：来源在制工单号
        row.put("wipWorkOrderNo", wipWorkOrderNo);
        return row;
    }

    private Map<String, Object> createShortageSummary(int shortageCount, int coveredCount,
                                                       int stockCoveredCount, int noBomCount) {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("shortageCount", shortageCount);
        summary.put("coveredCount", coveredCount);
        summary.put("stockCoveredCount", stockCoveredCount);
        summary.put("noBomCount", noBomCount);
        return summary;
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> checkOrderShortageWithDetail(Long orderId) {
        log.info("订单齐套检查开始: orderId={}", orderId);
        Map<String, Object> calculation = calculateOrderShortage(orderId);
        String orderNo = (String) calculation.get("orderNo");
        if (orderNo == null) {
            log.warn("订单不存在，跳过齐套检查: {}", orderId);
            return new ArrayList<>();
        }

        List<InventoryAlertLog> oldShortageAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getAlertType, "order_shortage")
                        .eq(InventoryAlertLog::getOrderNo, orderNo)
                        .in(InventoryAlertLog::getStatus, 0, 1));
        for (InventoryAlertLog oldAlert : oldShortageAlerts) {
            oldAlert.setStatus(3);
            oldAlert.setProcessRemark("齐套检查重算，旧预警失效");
            alertLogMapper.updateById(oldAlert);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> materialRows = (List<Map<String, Object>>) calculation.get("materialRows");
        List<Map<String, Object>> detailList = new ArrayList<>();
        for (Map<String, Object> item : materialRows) {
            if (!"shortage".equals(item.get("status"))) continue;
            Long materialId = (Long) item.get("materialId");
            BigDecimal demand = (BigDecimal) item.get("demand");
            BigDecimal available = (BigDecimal) item.get("available");
            BigDecimal inTransit = (BigDecimal) item.get("inTransit");
            BigDecimal actualGap = (BigDecimal) item.get("actualGap");
            String msg = "订单[" + orderNo + "]缺料：物料[" + item.get("materialCode") + "] "
                    + item.get("materialName") + " 需求" + demand.stripTrailingZeros().toPlainString()
                    + " 可用" + available.stripTrailingZeros().toPlainString()
                    + (inTransit.compareTo(BigDecimal.ZERO) > 0 ? " 在途" + inTransit.stripTrailingZeros().toPlainString() : "")
                    + " 实际缺口" + actualGap.stripTrailingZeros().toPlainString();
            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("order_shortage");
            alert.setAlertLevel("warning");
            alert.setOrderNo(orderNo);
            alert.setMaterialId(materialId);
            alert.setMaterialCode((String) item.get("materialCode"));
            alert.setMaterialName((String) item.get("materialName"));
            alert.setCurrentStock(available);
            alert.setSuggestion("建议补货 " + actualGap.stripTrailingZeros().toPlainString());
            alert.setAlertMessage(msg);
            alert.setAlertTime(LocalDateTime.now());
            alertLogMapper.insert(alert);
            Map<String, Object> detail = new java.util.LinkedHashMap<>(item);
            detail.remove("status");
            detailList.add(detail);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) calculation.get("summary");
        int shortageCount = ((Number) summary.get("shortageCount")).intValue();
        int coveredCount = ((Number) summary.get("coveredCount")).intValue();
        int noBomCount = ((Number) summary.get("noBomCount")).intValue();
        int stockCoveredCount = ((Number) summary.get("stockCoveredCount")).intValue();
        // 缺料联动（DEV-573 8-04）：触发 stock.shortage 事件通知采购/计划角色（配置表 target_role 控制）
        if (shortageCount > 0) {
            try {
                SalesOrder order = orderMapper.selectById(orderId);
                eventPublisher.fire("stock.shortage", java.util.Map.of(
                        "bizNo", orderNo,
                        "orderNo", orderNo,
                        "orderId", String.valueOf(order.getOrderId()),
                        "shortageCount", String.valueOf(shortageCount),
                        "noBomCount", String.valueOf(noBomCount),
                        "bizType", "order"));
            } catch (Exception e) {
                log.warn("订单缺料事件联动失败: {}", e.getMessage());
            }
        }
        log.info("订单{}齐套检查完成：缺料{}条，在途已覆盖{}条，无BOM产品{}个，产品现货覆盖{}条", orderNo, shortageCount, coveredCount, noBomCount, stockCoveredCount);
        return detailList;
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> orderOccupancyOverview() {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        List<SalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .in(SalesOrder::getOrderStatus, 4, 6, 7));
        if (orders == null || orders.isEmpty()) {
            return result;
        }
        orders.sort(this::compareOrderPriority);
        for (SalesOrder o : orders) {
            Map<Long, BigDecimal[]> occ = new java.util.HashMap<>();
            try {
                List<Map<String, Object>> occRows = salesOrderStockReserveMapper.sumActiveByProductAndType(o.getOrderId());
                if (occRows != null) {
                    for (Map<String, Object> r : occRows) {
                        if (r.get("productId") == null) continue;
                        long pid = ((Number) r.get("productId")).longValue();
                        int type = r.get("reserveType") == null ? 1 : ((Number) r.get("reserveType")).intValue();
                        BigDecimal q = toDecimal(r.get("qty"));
                        BigDecimal[] arr = occ.computeIfAbsent(pid, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                        if (type == 2) arr[1] = arr[1].add(q); else arr[0] = arr[0].add(q);
                    }
                }
            } catch (Exception e) {
                log.warn("占用总览-查询占用失败: orderId={}, err={}", o.getOrderId(), e.getMessage());
            }
            List<SalesOrderProduct> ps = orderProductMapper.selectList(new LambdaQueryWrapper<SalesOrderProduct>()
                    .eq(SalesOrderProduct::getOrderId, o.getOrderId()));
            if (ps == null) continue;
            for (SalesOrderProduct p : ps) {
                if (p.getProductId() == null) continue;
                BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                BigDecimal[] arr = occ.getOrDefault(p.getProductId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("orderNo", o.getOrderNo());
                row.put("orderStatus", o.getOrderStatus());
                row.put("isUrgent", o.getIsUrgent());
                row.put("productCode", p.getProductCode());
                row.put("productName", p.getProductName());
                row.put("orderQty", orderQty);
                row.put("reservedQty", arr[0]);
                row.put("pendingQty", arr[1]);
                row.put("wipWorkOrderNo", productWipWorkOrderNos(o.getOrderId(), p.getProductId()));
                BigDecimal remaining = orderQty.subtract(arr[0]).subtract(arr[1]);
                row.put("remainingQty", remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO);
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public void checkGlobalShortage() {
        log.info("全局汇总缺料检查开始（082定稿：订单缺料预警主逻辑）");
        // 1. 在途订单：已审核(4)/已确认(6)/生产中(7)
        List<SalesOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<SalesOrder>()
                        .in(SalesOrder::getOrderStatus, 4, 6, 7));
        if (orders == null || orders.isEmpty()) {
            log.info("无在途订单，跳过全局缺料检查");
            return;
        }

        // 2. 幂等：清掉旧的未处理物料维度缺料预警（demand_shortage）
        alertLogMapper.delete(new LambdaQueryWrapper<InventoryAlertLog>()
                .eq(InventoryAlertLog::getAlertType, "demand_shortage")
                .eq(InventoryAlertLog::getStatus, 0));

        // 3. 两步走汇总：产品维度先扣产品库存→还需生产→BOM展开→物料需求汇总
        Map<Long, BigDecimal> demandMap = new java.util.HashMap<>();
        Map<Long, String> codeMap = new java.util.HashMap<>();
        Map<Long, String> nameMap = new java.util.HashMap<>();
        // 2026-08-12：物料 → 涉及订单集合（全局缺料合并用）
        Map<Long, java.util.Set<Long>> orderSetMap = new java.util.HashMap<>();
        int noBomCount = 0;
        for (SalesOrder order : orders) {
            List<SalesOrderProduct> products = orderProductMapper.selectList(
                    new LambdaQueryWrapper<SalesOrderProduct>()
                            .eq(SalesOrderProduct::getOrderId, order.getOrderId()));
            if (products == null || products.isEmpty()) {
                continue;
            }
            for (SalesOrderProduct p : products) {
                if (p.getProductId() == null) {
                    continue;
                }
                // dev-20260930-018：净现货（扣优先级更高订单的未满足需求）；BOM 按"现货缺口"展开（不含在制）
                BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                BigDecimal productAvailable = productNetSpotAvailable(p.getProductId(), order.getOrderId());
                BigDecimal needProduce = orderQty.subtract(productAvailable);
                if (needProduce.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // 现货净可用足够，无需备料
                }
                // 第二步：还需生产量 BOM 展开
                EngineeringBom bom = bomMapper.selectOne(new LambdaQueryWrapper<EngineeringBom>()
                        .eq(EngineeringBom::getProductId, p.getProductId())
                        .eq(EngineeringBom::getIsCurrent, true)
                        .eq(EngineeringBom::getApproveStatus, 3));
                if (bom == null) {
                    noBomCount++;
                    continue;
                }
                List<EngineeringBomItem> items = bomItemMapper.selectList(
                        new LambdaQueryWrapper<EngineeringBomItem>()
                                .eq(EngineeringBomItem::getBomId, bom.getBomId()));
                for (EngineeringBomItem item : items) {
                    if (item.getMaterialId() == null) continue;
                    BigDecimal need = batchDemand(item, needProduce);
                    demandMap.merge(item.getMaterialId(), need, BigDecimal::add);
                    codeMap.putIfAbsent(item.getMaterialId(), item.getMaterialCode());
                    nameMap.putIfAbsent(item.getMaterialId(), item.getMaterialName());
                    orderSetMap.computeIfAbsent(item.getMaterialId(), k -> new java.util.HashSet<>()).add(order.getOrderId());
                }
            }
        }
        if (demandMap.isEmpty()) {
            log.info("全局缺料检查：无物料缺口（全部现货覆盖或无BOM）");
            return;
        }

        // 4. 对比可用库存+在途采购，缺口生成物料维度预警
        java.util.Map<Long, java.math.BigDecimal> inTransitMap = new java.util.HashMap<>();
        try {
            java.util.List<java.util.Map<String, Object>> transitRows = purchaseOrderItemMapper.selectInTransitByMaterial();
            for (java.util.Map<String, Object> row : transitRows) {
                Object mid = row.get("material_id");
                Object qty = row.get("in_transit");
                if (mid != null && qty != null) {
                    inTransitMap.put(((Number) mid).longValue(), new BigDecimal(qty.toString()));
                }
            }
        } catch (Exception e) {
            log.warn("全局缺料-查询在途采购量失败: {}", e.getMessage());
        }
        int shortageCount = 0;
        for (Map.Entry<Long, BigDecimal> entry : demandMap.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal demand = entry.getValue();
            InventoryStock stock = stockMapper.selectByMaterialId(materialId);
            BigDecimal available = (stock != null && stock.getAvailableQuantity() != null)
                    ? stock.getAvailableQuantity() : BigDecimal.ZERO;
            // dev-20260930-018（P1）：可用量 = 现货可用 − 安全库存；不再扣材料预占（旧账留待 P2 统一）
            available = available.subtract(safeStockOf(materialId));
            BigDecimal inTransit = inTransitMap.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal actualGap = demand.subtract(available).subtract(inTransit);
            if (actualGap.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 可用+在途已覆盖
            }
            String msg = "全局缺料：物料[" + codeMap.get(materialId) + "] " + nameMap.get(materialId)
                    + " 总需求" + demand.stripTrailingZeros().toPlainString()
                    + " 可用" + available.stripTrailingZeros().toPlainString()
                    + (inTransit.compareTo(BigDecimal.ZERO) > 0 ? " 在途" + inTransit.stripTrailingZeros().toPlainString() : "")
                    + " 实际缺口" + actualGap.stripTrailingZeros().toPlainString();
            int involvedCount = orderSetMap.getOrDefault(materialId, java.util.Collections.emptySet()).size();
            // 2026-08-12：同物料已有未处理订单缺料 → 全局信息合并进订单行，不再重复生成全局行
            List<InventoryAlertLog> existOrderAlerts = alertLogMapper.selectList(
                    new LambdaQueryWrapper<InventoryAlertLog>()
                            .eq(InventoryAlertLog::getAlertType, "order_shortage")
                            .eq(InventoryAlertLog::getMaterialId, materialId)
                            .eq(InventoryAlertLog::getStatus, 0)
                            .orderByDesc(InventoryAlertLog::getAlertTime)
                            .last("LIMIT 1"));
            if (existOrderAlerts != null && !existOrderAlerts.isEmpty()) {
                InventoryAlertLog target = existOrderAlerts.get(0);
                String mergedMsg = (target.getAlertMessage() == null ? "" : target.getAlertMessage())
                        + "；全局合计缺口" + actualGap.stripTrailingZeros().toPlainString()
                        + (involvedCount > 0 ? "，涉及" + involvedCount + "个订单" : "");
                InventoryAlertLog upd = new InventoryAlertLog();
                upd.setAlertId(target.getAlertId());
                upd.setAlertMessage(mergedMsg);
                upd.setInvolvedOrders(involvedCount > 0 ? involvedCount : null);
                upd.setSuggestion("建议补货 " + actualGap.stripTrailingZeros().toPlainString());
                alertLogMapper.updateById(upd);
                shortageCount++;
                continue;
            }
            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("demand_shortage");
            alert.setAlertLevel("warning");
            alert.setMaterialId(materialId);
            alert.setMaterialCode(codeMap.get(materialId));
            alert.setMaterialName(nameMap.get(materialId));
            alert.setCurrentStock(available);
            alert.setInvolvedOrders(involvedCount > 0 ? involvedCount : null);
            alert.setSuggestion("建议补货 " + actualGap.stripTrailingZeros().toPlainString());
            alert.setAlertMessage(msg);
            alert.setAlertTime(LocalDateTime.now());
            alertLogMapper.insert(alert);
            shortageCount++;
        }
        // 缺料联动通知采购/计划
        if (shortageCount > 0) {
            try {
                eventPublisher.fire("stock.shortage", java.util.Map.of(
                        "bizNo", "全部订单",
                        "shortageCount", String.valueOf(shortageCount),
                        "noBomCount", String.valueOf(noBomCount),
                        "bizType", "global"));
            } catch (Exception e) {
                log.warn("全局缺料事件联动失败: {}", e.getMessage());
            }
        }
        log.info("全局汇总缺料检查完成：物料缺口{}条，无BOM产品{}个", shortageCount, noBomCount);
    }

    @Override
    public long countUnprocessedOrderShortage(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return 0;
        }
        return alertLogMapper.selectCount(new LambdaQueryWrapper<InventoryAlertLog>()
                .eq(InventoryAlertLog::getAlertType, "order_shortage")
                .eq(InventoryAlertLog::getOrderNo, order.getOrderNo())
                .eq(InventoryAlertLog::getStatus, 0));
    }

    /**
     * 产品安全库存预警检查（080：产品也加安全库存预警，口径=可用量<安全库存）
     */
    private BigDecimal getProductAvailable(Long productId) {
        com.jjx.inventory.domain.InventoryItem item = inventoryItemMapper.selectBySource("PRODUCT", productId);
        if (item == null) return BigDecimal.ZERO;
        InventoryStock stock = stockMapper.selectByInventoryItemId(item.getInventoryItemId());
        if (stock == null || stock.getTotalQuantity() == null) return BigDecimal.ZERO;
        return stock.getTotalQuantity().subtract(
                stock.getTotalReserved() == null ? BigDecimal.ZERO : stock.getTotalReserved());
    }

    /**
     * dev-20260930-018（P1）：材料安全库存（inventory_material.safe_stock），查不到按 0。
     */
    private BigDecimal safeStockOf(Long materialId) {
        try {
            InventoryMaterial material = materialMapper.selectById(materialId);
            if (material != null && material.getSafeStock() != null) {
                return material.getSafeStock();
            }
        } catch (Exception e) {
            log.warn("查询安全库存失败(按0处理): materialId={}, err={}", materialId, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * dev-20260930-018（P1）：成品"净现货可用" =
     * max(0, 现货总量 − Σ 优先级高于本单的有效订单(已审核4/已确认6/生产中7)未满足需求)。
     * 优先级（先审先占的稳定代理）：加急(is_urgent=1)优先 &gt; 先建单(create_time)优先 &gt; order_id 小优先；
     * 本单不扣自己（天然排除）。
     */
    private BigDecimal productNetSpotAvailable(Long productId, Long currentOrderId) {
        BigDecimal onHand = getProductAvailable(productId);
        if (currentOrderId == null) {
            return onHand;
        }
        BigDecimal priorUnmet = BigDecimal.ZERO;
        try {
            SalesOrder current = orderMapper.selectById(currentOrderId);
            List<Map<String, Object>> rows = orderProductMapper.selectEffectiveDemandByProduct(productId);
            if (rows != null && current != null) {
                for (Map<String, Object> row : rows) {
                    Object oidObj = row.get("order_id");
                    if (oidObj == null) continue;
                    long oid = ((Number) oidObj).longValue();
                    if (oid == currentOrderId) continue;
                    if (isBeforeInAllocation(row, current)) {
                        priorUnmet = priorUnmet.add(unmetDemand(row));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("计算成品净现货可用失败(按现货保守处理): productId={}, currentOrder={}, err={}",
                    productId, currentOrderId, e.getMessage());
        }
        BigDecimal net = onHand.subtract(priorUnmet);
        return net.compareTo(BigDecimal.ZERO) > 0 ? net : BigDecimal.ZERO;
    }

    private BigDecimal unmetDemand(Map<String, Object> row) {
        BigDecimal unmet = toDecimal(row.get("demand")).subtract(toDecimal(row.get("shipped")));
        return unmet.compareTo(BigDecimal.ZERO) > 0 ? unmet : BigDecimal.ZERO;
    }

    /** 排序键：row 是否排在 current 之前（加急 &gt; 先建单 &gt; order_id 小）。 */
    private boolean isBeforeInAllocation(Map<String, Object> row, SalesOrder current) {
        int rowUrgent = row.get("is_urgent") == null ? 0 : ((Number) row.get("is_urgent")).intValue();
        int curUrgent = current.getIsUrgent() == null ? 0 : current.getIsUrgent();
        if (rowUrgent != curUrgent) {
            return rowUrgent > curUrgent;
        }
        java.util.Date rowTime = toDate(row.get("create_time"));
        java.util.Date curTime = toDate(current.getCreateTime());
        if (rowTime != null && curTime != null) {
            int c = rowTime.compareTo(curTime);
            if (c != 0) return c < 0;
        } else if (rowTime != null) {
            return true;
        } else if (curTime != null) {
            return false;
        }
        return ((Number) row.get("order_id")).longValue() < current.getOrderId();
    }

    private BigDecimal toDecimal(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private java.util.Date toDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.util.Date) return (java.util.Date) o;
        if (o instanceof java.time.LocalDate) return java.sql.Date.valueOf((java.time.LocalDate) o);
        if (o instanceof java.time.LocalDateTime) return java.sql.Timestamp.valueOf((java.time.LocalDateTime) o);
        return null;
    }

    /**
     * dev-20260930-018（P1）：本单在该成品上的在制未完工量（Σ production_order.remaining_quantity）。
     * 在制未完工 = 已审核2/已计划4/待开始5/进行中6/已暂停7/已超期11。
     */
    private BigDecimal productWipRemaining(Long salesOrderId, Long productId) {
        if (salesOrderId == null || productId == null) return BigDecimal.ZERO;
        try {
            List<com.jjx.production.domain.entity.ProductionOrder> wos =
                    productionOrderMapper.selectBySalesOrderId(salesOrderId);
            if (wos == null || wos.isEmpty()) return BigDecimal.ZERO;
            BigDecimal sum = BigDecimal.ZERO;
            for (com.jjx.production.domain.entity.ProductionOrder wo : wos) {
                if (!productId.equals(wo.getProductId())) continue;
                Integer st = wo.getOrderStatus();
                if (st == null || (st != 2 && st != 4 && st != 5 && st != 6 && st != 7 && st != 11)) continue;
                if (wo.getRemainingQuantity() != null) sum = sum.add(wo.getRemainingQuantity());
            }
            return sum;
        } catch (Exception e) {
            log.warn("查询本单在制失败(按0处理): orderId={}, productId={}, err={}",
                    salesOrderId, productId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** dev-20260930-023（P4a pegging）：本单在该成品上的来源在制工单号（逗号分隔，无则 null） */
    private String productWipWorkOrderNos(Long salesOrderId, Long productId) {
        if (salesOrderId == null || productId == null) return null;
        try {
            List<com.jjx.production.domain.entity.ProductionOrder> wos =
                    productionOrderMapper.selectBySalesOrderId(salesOrderId);
            if (wos == null || wos.isEmpty()) return null;
            java.util.List<String> nos = new java.util.ArrayList<>();
            for (com.jjx.production.domain.entity.ProductionOrder wo : wos) {
                if (!productId.equals(wo.getProductId())) continue;
                Integer st = wo.getOrderStatus();
                if (st == null || (st != 2 && st != 4 && st != 5 && st != 6 && st != 7 && st != 11)) continue;
                if (wo.getOrderNo() != null) nos.add(wo.getOrderNo());
            }
            return nos.isEmpty() ? null : String.join(",", nos);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * dev-20260930-021（P3）：材料层「跨订单分摊」——按订单优先级（加急 &gt; 先建单 &gt; order_id）
     * 把全厂供给（库存可用−安全库存 + 在途）依次分给所有有效订单(已审核4/已确认6/生产中7) + extra，
     * 每单只背自己的需求缺口。返回 orderId → (materialId → 该单分摊缺口)。
     */
    private Map<Long, Map<Long, BigDecimal>> allocateMaterialShortageByOrder(java.util.Set<Long> extraOrderIds) {
        Map<Long, Map<Long, BigDecimal>> result = new java.util.HashMap<>();
        List<SalesOrder> all = new java.util.ArrayList<>();
        List<SalesOrder> effective = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .in(SalesOrder::getOrderStatus, 4, 6, 7));
        if (effective != null) all.addAll(effective);
        if (extraOrderIds != null) {
            for (Long id : extraOrderIds) {
                if (id == null) continue;
                boolean present = false;
                for (SalesOrder o : all) { if (id.equals(o.getOrderId())) { present = true; break; } }
                if (!present) {
                    SalesOrder o = orderMapper.selectById(id);
                    if (o != null) all.add(o);
                }
            }
        }
        all.sort(this::compareOrderPriority);

        // dev-20260930-025（P4c 时间轴）：在途按预计到货日分档；每单只算 ≤ 本单交期的到货，先到先用
        Map<Long, java.util.List<Object[]>> transitByMat = new java.util.HashMap<>();
        try {
            List<Map<String, Object>> rows = purchaseOrderItemMapper.selectInTransitByMaterialAndDate();
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    Object mid = r.get("material_id");
                    Object q = r.get("qty");
                    if (mid == null || q == null) continue;
                    java.util.Date d = toDate(r.get("expected_date"));
                    transitByMat.computeIfAbsent(((Number) mid).longValue(), k -> new java.util.ArrayList<>())
                            .add(new Object[]{d, new BigDecimal(q.toString())});
                }
            }
        } catch (Exception e) {
            log.warn("P4c-查询在途(按到货日)失败(按0处理): {}", e.getMessage());
        }
        Map<Long, BigDecimal> stockRemaining = new java.util.HashMap<>();

        for (SalesOrder o : all) {
            Map<Long, BigDecimal> needByMat = new java.util.HashMap<>();
            List<SalesOrderProduct> ps = orderProductMapper.selectList(new LambdaQueryWrapper<SalesOrderProduct>()
                    .eq(SalesOrderProduct::getOrderId, o.getOrderId()));
            if (ps != null) {
                for (SalesOrderProduct p : ps) {
                    if (p.getProductId() == null) continue;
                    BigDecimal qty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                    BigDecimal spot = productNetSpotAvailable(p.getProductId(), o.getOrderId());
                    BigDecimal gap = qty.subtract(spot);
                    if (gap.compareTo(BigDecimal.ZERO) <= 0) continue;
                    EngineeringBom bom = bomMapper.selectOne(new LambdaQueryWrapper<EngineeringBom>()
                            .eq(EngineeringBom::getProductId, p.getProductId())
                            .eq(EngineeringBom::getIsCurrent, true)
                            .eq(EngineeringBom::getApproveStatus, 3));
                    if (bom == null) continue;
                    List<EngineeringBomItem> items = bomItemMapper.selectList(new LambdaQueryWrapper<EngineeringBomItem>()
                            .eq(EngineeringBomItem::getBomId, bom.getBomId()));
                    for (EngineeringBomItem it : items) {
                        if (it.getMaterialId() == null) continue;
                        needByMat.merge(it.getMaterialId(), batchDemand(it, gap), BigDecimal::add);
                    }
                }
            }
            Map<Long, BigDecimal> shortage = new java.util.HashMap<>();
            for (Map.Entry<Long, BigDecimal> e : needByMat.entrySet()) {
                Long mid = e.getKey();
                BigDecimal need = e.getValue();
                java.util.Date cutoff = o.getDeliveryDate();
                BigDecimal stockBal = stockRemaining.computeIfAbsent(mid, this::materialStockAvailable);
                java.util.List<Object[]> trs = transitByMat.get(mid);
                // 本单可用 = 库存余额 + 到货日 ≤ 本单交期的在途余额
                BigDecimal avail = stockBal;
                if (trs != null) {
                    for (Object[] t : trs) {
                        java.util.Date d = (java.util.Date) t[0];
                        if (cutoff == null || d == null || !d.after(cutoff)) {
                            avail = avail.add((BigDecimal) t[1]);
                        }
                    }
                }
                BigDecimal take = avail.compareTo(BigDecimal.ZERO) > 0 ? need.min(avail) : BigDecimal.ZERO;
                // 消耗：先扣库存余额，再按到货日升序扣在途余额
                BigDecimal left = take;
                BigDecimal fromStock = left.min(stockBal.compareTo(BigDecimal.ZERO) > 0 ? stockBal : BigDecimal.ZERO);
                stockRemaining.put(mid, stockBal.subtract(fromStock));
                left = left.subtract(fromStock);
                if (left.compareTo(BigDecimal.ZERO) > 0 && trs != null) {
                    java.util.List<Object[]> sorted = new java.util.ArrayList<>(trs);
                    sorted.sort((x, y) -> {
                        java.util.Date dx = (java.util.Date) x[0];
                        java.util.Date dy = (java.util.Date) y[0];
                        if (dx == null && dy == null) return 0;
                        if (dx == null) return 1;
                        if (dy == null) return -1;
                        return dx.compareTo(dy);
                    });
                    for (Object[] t : sorted) {
                        if (left.compareTo(BigDecimal.ZERO) <= 0) break;
                        java.util.Date d = (java.util.Date) t[0];
                        if (cutoff != null && d != null && d.after(cutoff)) continue;
                        BigDecimal bal = (BigDecimal) t[1];
                        if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
                        BigDecimal cx = left.min(bal);
                        t[1] = bal.subtract(cx);
                        left = left.subtract(cx);
                    }
                }
                BigDecimal s = need.subtract(take);
                if (s.compareTo(BigDecimal.ZERO) > 0) shortage.put(mid, s);
            }
            result.put(o.getOrderId(), shortage);
        }
        return result;
    }

    /** dev-20260930-025（P4c）：材料库存可用余额（可用量 − 安全库存） */
    private BigDecimal materialStockAvailable(Long materialId) {
        InventoryStock stock = stockMapper.selectByMaterialId(materialId);
        BigDecimal avail = (stock != null && stock.getAvailableQuantity() != null)
                ? stock.getAvailableQuantity() : BigDecimal.ZERO;
        return avail.subtract(safeStockOf(materialId));
    }

    /** 订单优先级：加急 &gt; 先建单(create_time) &gt; order_id 小（与占用/入库同口径） */
    private int compareOrderPriority(SalesOrder a, SalesOrder b) {
        int ua = (a != null && a.getIsUrgent() != null) ? a.getIsUrgent() : 0;
        int ub = (b != null && b.getIsUrgent() != null) ? b.getIsUrgent() : 0;
        if (ua != ub) return ub - ua;
        LocalDateTime ta = a != null ? a.getCreateTime() : null;
        LocalDateTime tb = b != null ? b.getCreateTime() : null;
        if (ta != null && tb != null) {
            int c = ta.compareTo(tb);
            if (c != 0) return c;
        } else if (ta != null) {
            return -1;
        } else if (tb != null) {
            return 1;
        }
        long ida = a != null ? a.getOrderId() : 0L;
        long idb = b != null ? b.getOrderId() : 0L;
        return Long.compare(ida, idb);
    }

    @Override
    public void checkSafeStockAlert() {
        log.info("检查安全库存预警");
        List<InventoryStock> lowStock = stockMapper.selectLowStock();
        // 2026-08-18：去重落库（status∈(0,1) 存在则更新，否则新建），修复每次检查重复 INSERT
        for (InventoryStock stock : lowStock) {
            BigDecimal available = stock.getTotalQuantity() != null ? stock.getTotalQuantity() : BigDecimal.ZERO;
            if (stock.getTotalReserved() != null) {
                available = available.subtract(stock.getTotalReserved());
            }
            BigDecimal safe = stock.getSafeStock() != null ? stock.getSafeStock() : BigDecimal.ZERO;
            BigDecimal suggestQty = safe.subtract(available);
            if (suggestQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            upsertLowStockAlert(stock, suggestQty);
        }
        // 2026-08-18：自动解除——库存已恢复（不在低库存列表）的未处理/已上报 safe_stock 预警 → 已解除(3)
        resolveRecoveredSafeStockAlerts(lowStock);
        try { if (!lowStock.isEmpty()) eventPublisher.fire("stock.low", Map.of("count", String.valueOf(lowStock.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("安全库存预警检查完成，发现 {} 条", lowStock.size());
    }

    /**
     * 2026-08-18：库存已恢复的安全库存预警自动解除（可用量已≥安全库存，无需采购）
     */
    private void resolveRecoveredSafeStockAlerts(List<InventoryStock> lowStock) {
        try {
            java.util.Set<Long> lowIds = lowStock.stream()
                    .map(InventoryStock::getMaterialId)
                    .collect(java.util.stream.Collectors.toSet());
            List<InventoryAlertLog> openAlerts = alertLogMapper.selectList(
                    new LambdaQueryWrapper<InventoryAlertLog>()
                            .eq(InventoryAlertLog::getAlertType, "safe_stock")
                            .in(InventoryAlertLog::getStatus, 0, 1));
            int resolved = 0;
            for (InventoryAlertLog alert : openAlerts) {
                if (alert.getMaterialId() == null || lowIds.contains(alert.getMaterialId())) continue;
                alert.setStatus(3);
                alert.setProcessRemark("库存已恢复，自动解除");
                alertLogMapper.updateById(alert);
                resolved++;
                log.info("安全库存预警自动解除: alertId={}, materialId={}", alert.getAlertId(), alert.getMaterialId());
            }
            if (resolved > 0) log.info("安全库存预警自动解除完成，共 {} 条", resolved);
        } catch (Exception e) {
            log.warn("自动解除安全库存预警失败: {}", e.getMessage());
        }
    }


    @Override
    public void checkSafeStockAlert(Long materialId) {
        log.info("检查单物料安全库存预警: materialId={}", materialId);
        InventoryStock stock = stockMapper.selectByMaterialId(materialId);
        if (stock == null) return;

        java.math.BigDecimal safe = java.math.BigDecimal.ZERO;
        try {
            // 2026-09-21（dev-20260921-005）：原实现用 QueryWrapper<InventoryStock> 去 select("safe_stock")，
            // 而 inventory_stock 根本没有该列 → SQLSyntaxErrorException: Unknown column 'safe_stock'，
            // 异常被下面的 catch 吞成 WARN，safe 恒为 0 → 第 590 行直接 return，
            // 单物料安全库存预警从未生效（每次确认入库都在日志里刷 WARN）。
            // 安全库存存在物料主数据 inventory_material.safe_stock（本类已注入 materialMapper）。
            InventoryMaterial material = materialMapper.selectById(materialId);
            if (material != null && material.getSafeStock() != null) {
                safe = material.getSafeStock();
            }
        } catch (Exception e) {
            log.warn("查询安全库存失败: {}", e.getMessage());
        }

        if (safe.compareTo(java.math.BigDecimal.ZERO) <= 0) return;
        // 064定稿：按可用量判断（total - reserved），禁止用总量
        java.math.BigDecimal availableQty = stock.getTotalQuantity() == null ? java.math.BigDecimal.ZERO : stock.getTotalQuantity();
        if (stock.getTotalReserved() != null) {
            availableQty = availableQty.subtract(stock.getTotalReserved());
        }
        if (availableQty.compareTo(safe) >= 0) return;

        // 2026-08-18：单物料检查也去重落库（复用 upsertLowStockAlert，修复重复 INSERT）
        upsertLowStockAlert(stock, safe.subtract(availableQty));

        try { eventPublisher.fire("stock.low", java.util.Map.of("materialId", String.valueOf(materialId), "currentStock", String.valueOf(stock.getTotalQuantity()), "safeStock", String.valueOf(safe))); }
        catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("单物料安全库存预警检查完成: materialId={}", materialId);
    }
    @Override
    public void checkMaxStockAlert() {
        log.info("检查最高库存预警");
        // 检查库存超过最高库存的物料
        LambdaQueryWrapper<InventoryStockItem> wrapper = new LambdaQueryWrapper<InventoryStockItem>()
                .gt(InventoryStockItem::getQuantity, 10000); // 简单阈值检查
        List<InventoryStockItem> overStock = stockItemMapper.selectList(wrapper);
        for (InventoryStockItem item : overStock) {
            String msg = "物料[" + item.getMaterialCode() + "] 库存: " + item.getQuantity() + ", 可能过高";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("max_stock");
            alert.setAlertLevel("info");
            alert.setMaterialId(item.getMaterialId());
            alert.setMaterialCode(item.getMaterialCode());
            alert.setMaterialName(item.getMaterialName());
            alert.setCurrentStock(item.getQuantity());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!overStock.isEmpty()) eventPublisher.fire("stock.over", Map.of("count", String.valueOf(overStock.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("最高库存预警检查完成，发现 {} 条", overStock.size());
    }

    @Override
    public void checkExpiryAlert() {
        log.info("检查保质期预警");
        List<InventoryStock> expiring = stockMapper.selectExpiring();
        for (InventoryStock stock : expiring) {
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 最早有效期: " + stock.getEarliestExpiry() + ", 即将过期";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("expiry");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setExpiryDate(stock.getEarliestExpiry());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!expiring.isEmpty()) eventPublisher.fire("stock.expiry", Map.of("count", String.valueOf(expiring.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("保质期预警检查完成，发现 {} 条", expiring.size());
    }

    @Override
    public void checkObsoleteAlert() {
        log.info("检查呆滞料预警");
        List<InventoryStock> obsolete = stockMapper.selectObsolete();
        for (InventoryStock stock : obsolete) {
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 库存: " + stock.getTotalQuantity() + ", 超过180天未出库";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("obsolete");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!obsolete.isEmpty()) eventPublisher.fire("stock.obsolete", Map.of("count", String.valueOf(obsolete.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("呆滞料预警检查完成，发现 {} 条", obsolete.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long alertId) {
        // 2026-08-18：语义升级为「标记已上报」——仓库确认并上报，留痕 reported_by/reported_time
        InventoryAlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null) {
            log.error("预警不存在: alertId={}", alertId);
            return false;
        }
        // 已处理/已解除的不可再上报
        if (Objects.equals(alert.getStatus(), 2) || Objects.equals(alert.getStatus(), 3)) {
            log.warn("预警已处理/已解除，不可上报: alertId={}, status={}", alertId, alert.getStatus());
            return false;
        }

        alert.setStatus(1);
        alert.setReportedBy(SecurityUtils.getUsername());
        alert.setReportedTime(LocalDateTime.now());
        return alertLogMapper.updateById(alert) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchMarkRead(List<Long> alertIds) {
        // 2026-08-18：语义升级为「标记已上报」
        if (alertIds == null || alertIds.isEmpty()) {
            return false;
        }

        List<InventoryAlertLog> alerts = alertLogMapper.selectBatchIds(alertIds);
        String user = SecurityUtils.getUsername();
        LocalDateTime now = LocalDateTime.now();
        for (InventoryAlertLog alert : alerts) {
            // 已处理/已解除的跳过
            if (Objects.equals(alert.getStatus(), 2) || Objects.equals(alert.getStatus(), 3)) {
                continue;
            }
            alert.setStatus(1);
            alert.setReportedBy(user);
            alert.setReportedTime(now);
        }

        return updateBatchById(alerts);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean processAlert(Long alertId, String processedBy, String remark) {
        InventoryAlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null) {
            log.error("预警不存在: alertId={}", alertId);
            return false;
        }

        alert.setStatus(2);
        alert.setProcessedBy(processedBy);
        alert.setProcessedTime(LocalDateTime.now());
        alert.setProcessRemark(remark);
        boolean updated = alertLogMapper.updateById(alert) > 0;
        if (updated) {
            publishAlertEvent("inventory.alert.processed", alert.getAlertId(), alert.getMaterialCode());
        }
        return updated;
    }

    @Override
    public List<Map<String, Object>> generatePurchaseSuggestions() {
        log.info("生成采购建议");
        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 来源1：低库存物料（安全库存算法，DEV-664：用物料 max_stock，无则 safe_stock*2 兜底）
        // DEV-20260810-014：低库存同时落库 safe_stock 预警（去重），保证有记录可闭环跟踪
        // DEV-815：在途采购量（已下采购订单未收货）——建议量扣除，避免重复建议
        java.util.Map<Long, java.math.BigDecimal> inTransitMap = new java.util.HashMap<>();
        try {
            java.util.List<java.util.Map<String, Object>> transitRows = purchaseOrderItemMapper.selectInTransitByMaterial();
            for (java.util.Map<String, Object> row : transitRows) {
                Object mid = row.get("material_id");
                Object qty = row.get("in_transit");
                if (mid != null && qty != null) {
                    inTransitMap.put(((Number) mid).longValue(), new BigDecimal(qty.toString()));
                }
            }
        } catch (Exception e) {
            log.warn("查询在途采购量失败: {}", e.getMessage());
        }

        List<InventoryStock> lowStock = stockMapper.selectLowStock();
        for (InventoryStock stock : lowStock) {
            // 查询物料最高库存参数
            BigDecimal maxStock = null;
            BigDecimal reorderPoint = null;
            Long supplierId = null;
            String supplierName = null;
            try {
                com.jjx.inventory.domain.InventoryMaterial mat = materialMapper.selectById(stock.getMaterialId());
                if (mat != null && mat.getMaxStock() != null) {
                    maxStock = mat.getMaxStock();
                }
                if (mat != null && mat.getReorderPoint() != null && mat.getReorderPoint().compareTo(BigDecimal.ZERO) > 0) {
                    reorderPoint = mat.getReorderPoint();
                }
                if (mat != null) {
                    supplierId = mat.getSupplierId();
                    supplierName = mat.getSupplierName();
                }
            } catch (Exception e) {
                log.warn("查询物料最高库存失败: materialId={}, err={}", stock.getMaterialId(), e.getMessage());
            }
            // 093定稿：触发源统一——库存≤再订货点→采购建议(提前补货)；低于安全库存→紧急预警(兜底)
            // 建议量 = max_stock - 当前库存 - 在途采购量；无 max_stock 时用 safe_stock*2 兜底
            BigDecimal target = maxStock != null ? maxStock
                    : (stock.getSafeStock() != null ? stock.getSafeStock().multiply(BigDecimal.valueOf(2)) : BigDecimal.valueOf(100));
            BigDecimal suggestQty = target.subtract(stock.getTotalQuantity() != null
                    ? stock.getTotalQuantity() : BigDecimal.ZERO);
            // DEV-815：扣除在途采购量（已下采购订单未收货），已下单在途则不再重复建议
            BigDecimal inTransit = inTransitMap.getOrDefault(stock.getMaterialId(), BigDecimal.ZERO);
            suggestQty = suggestQty.subtract(inTransit);
            if (suggestQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 再订货点触发（库存≤再订货点）即使未低于安全库存也建议补货（提前补货）
            String reason = "低于安全库存，建议补货";
            String priority = "normal";
            BigDecimal available = stock.getTotalQuantity() != null ? stock.getTotalQuantity() : BigDecimal.ZERO;
            if (stock.getTotalReserved() != null) {
                available = available.subtract(stock.getTotalReserved());
            }
            if (reorderPoint != null && available.compareTo(reorderPoint) <= 0) {
                reason = "库存≤再订货点(" + reorderPoint.stripTrailingZeros().toPlainString() + ")，提前补货";
            }

            // 落库/去重更新 safe_stock 预警
            Long alertId = upsertLowStockAlert(stock, suggestQty);

            Map<String, Object> row = new HashMap<>();
            row.put("materialId", stock.getMaterialId());
            row.put("materialCode", stock.getMaterialCode());
            row.put("materialName", stock.getMaterialName());
            row.put("currentStock", stock.getTotalQuantity() != null ? stock.getTotalQuantity().doubleValue() : 0);
            row.put("suggestQuantity", suggestQty.doubleValue());
            row.put("reason", reason);
            row.put("priority", priority);
            row.put("sourceAlertId", alertId);
            if (supplierId != null) row.put("supplierId", supplierId);
            if (supplierName != null && !supplierName.isBlank()) row.put("supplierName", supplierName);
            suggestions.add(row);
        }

        // 来源2：未处理的订单缺料 / 全局汇总缺料预警（DEV-573 8-04 衔接齐套检查）
        // 2026-08-18：含已上报(1)——已上报同样进采购待办（修复标记已读后从建议消失的断链）
        // dev-20260929-025：**demand_shortage（全局汇总缺料）原先被漏掉** —— 齐套检查产生的
        //   "多单汇总缺料"只会落 demand_shortage，而这里只捞 order_shortage ⇒ 采购计划永远看不到缺口。
        List<InventoryAlertLog> shortageAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .in(InventoryAlertLog::getAlertType, "order_shortage", "demand_shortage")
                        .in(InventoryAlertLog::getStatus, 0, 1));
        for (InventoryAlertLog alert : shortageAlerts) {
            // 缺口 = 需求 - 可用，建议补货量取缺口（从 alertMessage 冗余在 suggestion 中，优先解析 suggestion）
            BigDecimal gap = BigDecimal.ZERO;
            if (alert.getSuggestion() != null && alert.getSuggestion().startsWith("建议补货 ")) {
                try {
                    gap = new BigDecimal(alert.getSuggestion().substring("建议补货 ".length()).trim());
                } catch (Exception e) { /* fallthrough */ }
            }
            if (gap.compareTo(BigDecimal.ZERO) <= 0) continue;

            Long supplierId = null;
            String supplierName = null;
            if (alert.getMaterialId() != null) {
                com.jjx.inventory.domain.InventoryMaterial mat = materialMapper.selectById(alert.getMaterialId());
                if (mat != null) {
                    supplierId = mat.getSupplierId();
                    supplierName = mat.getSupplierName();
                }
            }

            Map<String, Object> row = new HashMap<>();
            row.put("materialId", alert.getMaterialId());
            row.put("materialCode", alert.getMaterialCode());
            row.put("materialName", alert.getMaterialName());
            row.put("currentStock", alert.getCurrentStock() != null ? alert.getCurrentStock().doubleValue() : 0);
            row.put("suggestQuantity", gap.doubleValue());
            // dev-20260929-025：demand_shortage（多单汇总缺料）没有单号，文案走兜底，别显示"订单[]缺料"
            String reasonText = alert.getOrderNo() != null && !alert.getOrderNo().isBlank()
                    ? "订单[" + alert.getOrderNo() + "]缺料，建议补货"
                    : "多单汇总缺料，建议补货";
            row.put("reason", reasonText);
            row.put("priority", "urgent");
            row.put("sourceAlertId", alert.getAlertId());
            if (supplierId != null) row.put("supplierId", supplierId);
            if (supplierName != null && !supplierName.isBlank()) row.put("supplierName", supplierName);
            suggestions.add(row);
        }

        log.info("生成采购建议完成，共 {} 条（低库存{} + 订单缺料{}）", suggestions.size(),
                lowStock.size(), shortageAlerts.size());
        return suggestions;
    }

    /**
     * DEV-20260810-014：低库存预警落库（同物料未处理预警存在则更新，不重复插）
     *
     * @return 预警ID（新建或已存在）
     */
    private Long upsertLowStockAlert(InventoryStock stock, BigDecimal suggestQty) {
        try {
            InventoryAlertLog exist = alertLogMapper.selectOne(
                    new LambdaQueryWrapper<InventoryAlertLog>()
                            .eq(InventoryAlertLog::getAlertType, "safe_stock")
                            .eq(InventoryAlertLog::getMaterialId, stock.getMaterialId())
                            // 2026-08-18 修复复燃：查任意状态，存在则只更新快照（保持原状态），不再新建
                            .last("LIMIT 1"));
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 当前库存: " + stock.getTotalQuantity() + ", 低于安全库存，建议补货 " + suggestQty.stripTrailingZeros().toPlainString();
            if (exist != null) {
                exist.setCurrentStock(stock.getTotalQuantity());
                exist.setSafeStock(stock.getSafeStock());
                exist.setSuggestion("建议补货 " + suggestQty.stripTrailingZeros().toPlainString());
                exist.setAlertMessage(msg);
                exist.setAlertTime(java.time.LocalDateTime.now());
                alertLogMapper.updateById(exist);
                return exist.getAlertId();
            }
            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("safe_stock");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setSafeStock(stock.getSafeStock());
            alert.setSuggestion("建议补货 " + suggestQty.stripTrailingZeros().toPlainString());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
            return alert.getAlertId();
        } catch (Exception e) {
            log.warn("低库存预警落库失败: materialId={}, err={}", stock.getMaterialId(), e.getMessage());
            return null;
        }
    }

    /**
     * DEV-20260810-014：批量处理预警（采购计划确认后回写：状态→已处理 + 处理人 + 关联采购订单号）
     */
    @Override
    public boolean batchProcessAlert(java.util.List<Long> alertIds, java.util.List<Long> materialIds,
                                     String relatedOrderNo, String remark) {
        // 2026-08-18：支持按物料回写——把勾选物料的所有未处理/已上报预警并入处理集合
        // （补手动添加行无 sourceAlertId、低库存预警复燃场景）
        java.util.Set<Long> idSet = new java.util.HashSet<>();
        if (alertIds != null) idSet.addAll(alertIds);
        try {
            idSet.addAll(getUnprocessedAlertIdsByMaterials(materialIds));
        } catch (Exception e) {
            log.warn("按物料查询未处理预警失败(忽略): {}", e.getMessage());
        }
        if (idSet.isEmpty()) {
            return true;
        }
        java.util.List<Long> merged = new java.util.ArrayList<>(idSet);
        String user;
        try {
            user = SecurityUtils.getUsername();
        } catch (Exception e) {
            user = "system";
        }
        int processed = 0;
        for (Long alertId : merged) {
            try {
                InventoryAlertLog alert = alertLogMapper.selectById(alertId);
                // 2026-08-18：已处理(2)/已解除(3)跳过，防已解除预警被重新置已处理
                if (alert == null || java.util.Objects.equals(alert.getStatus(), 2)
                        || java.util.Objects.equals(alert.getStatus(), 3)) {
                    continue;
                }
                alert.setStatus(2);
                alert.setProcessedBy(user);
                alert.setProcessedTime(java.time.LocalDateTime.now());
                String r = remark != null ? remark : "";
                if (relatedOrderNo != null && !relatedOrderNo.isEmpty()) {
                    r = (r.isEmpty() ? "" : r + "；") + "生成采购订单 " + relatedOrderNo;
                }
                alert.setProcessRemark(r);
                alertLogMapper.updateById(alert);
                processed++;
            } catch (Exception e) {
                log.warn("处理预警失败: alertId={}, err={}", alertId, e.getMessage());
            }
        }
        log.info("批量处理预警完成：{} 条（关联订单 {}）", processed, relatedOrderNo);
        return true;
    }

    @Override
    public List<AlertVO> getUnprocessed() {
        // 2026-08-18：含已上报(1)——统计口径为"待处理（未上报+已上报）"
        List<InventoryAlertLog> alerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .in(InventoryAlertLog::getStatus, 0, 1)
                        .orderByDesc(InventoryAlertLog::getAlertTime)
        );
        return convertToVOList(alerts);
    }

    @Override
    public boolean existsUnprocessed(String alertType, Long materialId) {
        Long count = alertLogMapper.selectCount(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getAlertType, alertType)
                        .eq(InventoryAlertLog::getMaterialId, materialId)
                        .eq(InventoryAlertLog::getStatus, 0)
        );
        return count != null && count > 0;
    }

    @Override
    public java.util.List<Long> getUnprocessedAlertIdsByMaterials(java.util.List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<InventoryAlertLog> alerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .in(InventoryAlertLog::getMaterialId, materialIds)
                        .in(InventoryAlertLog::getStatus, 0, 1) // 未处理/已读都可回写，已处理(2)跳过
        );
        return alerts.stream().map(InventoryAlertLog::getAlertId).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public IPage<InventoryAlertLog> pageQuery(Map<String, Object> params) {
        String alertType = (String) params.get("alertType");
        String alertLevel = (String) params.get("alertLevel");
        String status = (String) params.get("status");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryAlertLog> wrapper = new LambdaQueryWrapper<>();
        if (alertType != null && !alertType.isEmpty()) {
            wrapper.eq(InventoryAlertLog::getAlertType, alertType);
        }
        if (alertLevel != null && !alertLevel.isEmpty()) {
            wrapper.eq(InventoryAlertLog::getAlertLevel, alertLevel);
        }
        if (status != null) {
            wrapper.eq(InventoryAlertLog::getStatus, Integer.valueOf(status));
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryAlertLog::getAlertTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryAlertLog::getAlertTime, endDate);
        }
        wrapper.orderByDesc(InventoryAlertLog::getAlertTime);

        Page<InventoryAlertLog> page = new Page<>(pageNum, pageSize);
        return alertLogMapper.selectPage(page, wrapper);
    }

    private BigDecimal getActualIssueQty(EngineeringBomItem item) {
        if (item.getActualIssueQty() != null) {
            return item.getActualIssueQty();
        }
        log.warn("BOM 明细 {} 缺实际投料，按应用料兜底，请重新保存 BOM", item.getItemId());
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal lossRate = BigDecimal.valueOf(item.getLossRate() != null ? item.getLossRate() : 0);
        return quantity.multiply(BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100))));
    }

    /**
     * 整批需求 = MAX( 向上取整( 单位投料 × 数量 ), 最低投料量 )
     * 单位投料 = actual_issue_qty（BOM 保存时写入的含损耗单位应用料，不取整）
     * 取整只在整批做一次；最低投料量作为下限（min_issue_qty>0 时生效，与物料类型无关）
     */
    private BigDecimal batchDemand(EngineeringBomItem item, BigDecimal quantity) {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal demand = getActualIssueQty(item).multiply(qty).setScale(0, java.math.RoundingMode.UP);
        BigDecimal minIssue = item.getMinIssueQty();
        if (minIssue != null && minIssue.compareTo(BigDecimal.ZERO) > 0 && demand.compareTo(minIssue) < 0) {
            demand = minIssue;
        }
        return demand;
    }

    private List<AlertVO> convertToVOList(List<InventoryAlertLog> alerts) {
        List<AlertVO> result = new ArrayList<>();
        for (InventoryAlertLog alert : alerts) {
            result.add(convertToVO(alert));
        }
        return result;
    }

    private AlertVO convertToVO(InventoryAlertLog alert) {
        if (alert == null) {
            return null;
        }

        AlertVO vo = new AlertVO();
        vo.setAlertId(alert.getAlertId());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setOrderNo(alert.getOrderNo());
        vo.setMaterialId(alert.getMaterialId());
        vo.setMaterialCode(alert.getMaterialCode());
        vo.setMaterialName(alert.getMaterialName());
        vo.setCurrentStock(alert.getCurrentStock());
        vo.setSafeStock(alert.getSafeStock());
        vo.setMaxStock(alert.getMaxStock());
        vo.setExpiryDate(alert.getExpiryDate());
        vo.setLastOutboundDate(alert.getLastOutboundDate());
        vo.setAlertMessage(alert.getAlertMessage());
        vo.setAlertTime(alert.getAlertTime());
        vo.setStatus(alert.getStatus());
        vo.setReportedBy(alert.getReportedBy());
        vo.setReportedTime(alert.getReportedTime());
        vo.setProcessedBy(alert.getProcessedBy());
        vo.setProcessedTime(alert.getProcessedTime());
        vo.setProcessRemark(alert.getProcessRemark());
        vo.setSuggestion(alert.getSuggestion());
        vo.setCreateTime(alert.getCreateTime());
        vo.setUpdateTime(alert.getUpdateTime());
        vo.setCreateBy(alert.getCreateBy());
        vo.setUpdateBy(alert.getUpdateBy());
        // 设置类型名称

        return vo;
    }

}
