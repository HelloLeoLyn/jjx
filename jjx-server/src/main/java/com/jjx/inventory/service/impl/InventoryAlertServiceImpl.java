package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryAlertLog;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.ProductStock;
import com.jjx.inventory.dto.query.AlertQueryDTO;
import com.jjx.inventory.dto.vo.AlertVO;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.ProductStockMapper;
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
import java.util.List;
import java.util.Map;
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
    private final ProductStockMapper productStockMapper;
    private final com.jjx.inventory.mapper.OrderMaterialReserveMapper orderMaterialReserveMapper;
    private final EventPublisher eventPublisher;
    private final OrderMapper orderMapper;
    private final SalesOrderProductMapper orderProductMapper;
    private final EngineeringBomMapper bomMapper;
    private final EngineeringBomItemMapper bomItemMapper;
    private final com.jjx.purchase.mapper.PurchaseOrderItemMapper purchaseOrderItemMapper;

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
        checkProductSafeStockAlert();
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
    public java.util.List<java.util.Map<String, Object>> checkOrderShortageWithDetail(Long orderId) {
        log.info("订单齐套检查开始: orderId={}", orderId);
        // 1. 查订单（拿订单号）
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在，跳过齐套检查: {}", orderId);
            return new java.util.ArrayList<>();
        }
        String orderNo = order.getOrderNo();

        // 2. 幂等：清掉该订单旧的未处理缺料预警
        alertLogMapper.delete(new LambdaQueryWrapper<InventoryAlertLog>()
                .eq(InventoryAlertLog::getAlertType, "order_shortage")
                .eq(InventoryAlertLog::getOrderNo, orderNo)
                .eq(InventoryAlertLog::getStatus, 0));

        // 3. 查订单明细
        List<SalesOrderProduct> products = orderProductMapper.selectList(
                new LambdaQueryWrapper<SalesOrderProduct>()
                        .eq(SalesOrderProduct::getOrderId, orderId));
        if (products == null || products.isEmpty()) {
            log.info("订单{}无明细，跳过齐套检查", orderNo);
            return new java.util.ArrayList<>();
        }

        // 4. 两步走（DEV-20260810-096）：先扣产品库存（产品维度），还需生产的量才 BOM 展开算物料需求
        // 例：需求1000，产品库存200→需生产800→BOM展开→物料缺口
        Map<Long, BigDecimal> demandMap = new java.util.HashMap<>();
        Map<Long, String> codeMap = new java.util.HashMap<>();
        Map<Long, String> nameMap = new java.util.HashMap<>();
        int noBomCount = 0;
        int stockCoveredCount = 0; // 产品现货直接覆盖的明细数
        for (SalesOrderProduct p : products) {
            if (p.getProductId() == null) {
                log.info("订单{}明细产品ID为空，跳过", orderNo);
                continue;
            }
            BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());

            // 第一步：先扣产品库存（产品维度现货优先）
            BigDecimal productAvailable = BigDecimal.ZERO;
            try {
                ProductStock ps = productStockMapper.selectByProductId(p.getProductId());
                if (ps != null && ps.getAvailableQuantity() != null) {
                    productAvailable = ps.getAvailableQuantity();
                }
            } catch (Exception e) {
                log.warn("查询产品库存失败(按无现货处理): productId={}, err={}", p.getProductId(), e.getMessage());
            }
            BigDecimal needProduce = orderQty.subtract(productAvailable);
            if (needProduce.compareTo(BigDecimal.ZERO) <= 0) {
                // 产品现货足够覆盖订单需求，无需生产，不产生物料需求
                stockCoveredCount++;
                log.info("产品{}现货足够（可用{}≥需求{}），无需生产，跳过BOM展开",
                        p.getProductCode(), productAvailable.stripTrailingZeros().toPlainString(), orderQty.stripTrailingZeros().toPlainString());
                continue;
            }

            // 第二步：还需生产的量才 BOM 展开
            // 生效已审批 BOM
            EngineeringBom bom = bomMapper.selectOne(new LambdaQueryWrapper<EngineeringBom>()
                    .eq(EngineeringBom::getProductId, p.getProductId())
                    .eq(EngineeringBom::getIsCurrent, true)
                    .eq(EngineeringBom::getApproveStatus, 3));
            if (bom == null) {
                noBomCount++;
                log.info("产品{}无生效已审批BOM，跳过（不阻断）", p.getProductCode());
                continue;
            }
            List<EngineeringBomItem> items = bomItemMapper.selectList(
                    new LambdaQueryWrapper<EngineeringBomItem>()
                            .eq(EngineeringBomItem::getBomId, bom.getBomId()));
            for (EngineeringBomItem item : items) {
                if (item.getMaterialId() == null) continue;
                BigDecimal unitQty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
                BigDecimal loss = BigDecimal.valueOf(item.getLossRate() == null ? 0 : item.getLossRate());
                BigDecimal need = needProduce.multiply(unitQty)
                        .multiply(BigDecimal.ONE.add(loss.divide(BigDecimal.valueOf(100))));
                demandMap.merge(item.getMaterialId(), need, BigDecimal::add);
                codeMap.putIfAbsent(item.getMaterialId(), item.getMaterialCode());
                nameMap.putIfAbsent(item.getMaterialId(), item.getMaterialName());
            }
        }

        // 5. 对比可用库存，缺口生成预警（DEV：扣除在途采购量，口径与采购建议一致）
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
        java.util.List<java.util.Map<String, Object>> detailList = new java.util.ArrayList<>();
        int shortageCount = 0;
        int coveredCount = 0;
        for (Map.Entry<Long, BigDecimal> entry : demandMap.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal demand = entry.getValue();
            InventoryStock stock = stockMapper.selectByMaterialId(materialId);
            BigDecimal available = (stock != null && stock.getAvailableQuantity() != null)
                    ? stock.getAvailableQuantity() : BigDecimal.ZERO;
            // 094：可用量口径 = 总量 - 预留 - 材料预占占用（所有在途订单预占都算）
            try {
                BigDecimal preReserve = orderMaterialReserveMapper.selectReserveByMaterial(materialId);
                if (preReserve.compareTo(BigDecimal.ZERO) > 0) {
                    available = available.subtract(preReserve);
                }
            } catch (Exception e) {
                log.warn("扣除材料预占占用失败(跳过): materialId={}", materialId);
            }
            BigDecimal inTransit = inTransitMap.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal actualGap = demand.subtract(available).subtract(inTransit);
            if (actualGap.compareTo(BigDecimal.ZERO) <= 0) {
                // 在途已覆盖：不再预警（销售可见在途覆盖情况，避免重复采购）
                if (demand.subtract(available).compareTo(BigDecimal.ZERO) > 0) {
                    coveredCount++;
                }
                continue;
            }

            String msg = "订单[" + orderNo + "]缺料：物料[" + codeMap.get(materialId) + "] "
                    + nameMap.get(materialId) + " 需求" + demand.stripTrailingZeros().toPlainString()
                    + " 可用" + available.stripTrailingZeros().toPlainString()
                    + (inTransit.compareTo(BigDecimal.ZERO) > 0 ? " 在途" + inTransit.stripTrailingZeros().toPlainString() : "")
                    + " 实际缺口" + actualGap.stripTrailingZeros().toPlainString();
            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("order_shortage");
            alert.setAlertLevel("warning");
            alert.setOrderNo(orderNo);
            alert.setMaterialId(materialId);
            alert.setMaterialCode(codeMap.get(materialId));
            alert.setMaterialName(nameMap.get(materialId));
            alert.setCurrentStock(available);
            alert.setSuggestion("建议补货 " + actualGap.stripTrailingZeros().toPlainString());
            alert.setAlertMessage(msg);
            alert.setAlertTime(LocalDateTime.now());
            alertLogMapper.insert(alert);
            shortageCount++;

            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("materialId", materialId);
            item.put("materialCode", codeMap.get(materialId));
            item.put("materialName", nameMap.get(materialId));
            item.put("demand", demand);
            item.put("available", available);
            item.put("inTransit", inTransit);
            item.put("actualGap", actualGap);
            detailList.add(item);
        }
        // 缺料联动（DEV-573 8-04）：触发 stock.shortage 事件通知采购/计划角色（配置表 target_role 控制）
        if (shortageCount > 0) {
            try {
                eventPublisher.fire("stock.shortage", java.util.Map.of(
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
                // 第一步：先扣产品库存（产品维度现货优先）
                BigDecimal orderQty = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                BigDecimal productAvailable = BigDecimal.ZERO;
                try {
                    ProductStock ps = productStockMapper.selectByProductId(p.getProductId());
                    if (ps != null && ps.getAvailableQuantity() != null) {
                        productAvailable = ps.getAvailableQuantity();
                    }
                } catch (Exception e) {
                    log.warn("全局缺料-查询产品库存失败(按0处理): productId={}", p.getProductId());
                }
                BigDecimal needProduce = orderQty.subtract(productAvailable);
                if (needProduce.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // 现货足够，无需生产
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
                    BigDecimal unitQty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
                    BigDecimal loss = BigDecimal.valueOf(item.getLossRate() == null ? 0 : item.getLossRate());
                    BigDecimal need = needProduce.multiply(unitQty)
                            .multiply(BigDecimal.ONE.add(loss.divide(BigDecimal.valueOf(100))));
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
            // 094：可用量口径 = 总量 - 预留 - 材料预占占用（所有在途订单预占都算）
            try {
                BigDecimal preReserve = orderMaterialReserveMapper.selectReserveByMaterial(materialId);
                if (preReserve.compareTo(BigDecimal.ZERO) > 0) {
                    available = available.subtract(preReserve);
                }
            } catch (Exception e) {
                log.warn("全局缺料-扣除材料预占占用失败(跳过): materialId={}", materialId);
            }
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
    public void checkProductSafeStockAlert() {
        try {
            List<ProductStock> all = productStockMapper.selectList(new LambdaQueryWrapper<ProductStock>()
                    .gt(ProductStock::getSafeStock, BigDecimal.ZERO));
            int lowCount = 0;
            for (ProductStock ps : all) {
                BigDecimal available = (ps.getAvailableQuantity() != null) ? ps.getAvailableQuantity()
                        : ps.getTotalQuantity().subtract(ps.getTotalReserved() == null ? BigDecimal.ZERO : ps.getTotalReserved());
                if (available.compareTo(ps.getSafeStock()) < 0) {
                    String msg = "产品[" + ps.getProductCode() + "] " + ps.getProductName()
                            + " 可用库存: " + available.stripTrailingZeros().toPlainString() + ", 低于安全库存 " + ps.getSafeStock().stripTrailingZeros().toPlainString();
                    log.warn(msg);
                    InventoryAlertLog alert = new InventoryAlertLog();
                    alert.setAlertType("safe_stock");
                    alert.setAlertLevel("warning");
                    alert.setMaterialId(ps.getProductId());
                    alert.setMaterialCode(ps.getProductCode());
                    alert.setMaterialName(ps.getProductName());
                    alert.setCurrentStock(available);
                    alert.setSafeStock(ps.getSafeStock());
                    alert.setAlertMessage(msg);
                    alert.setAlertTime(java.time.LocalDateTime.now());
                    alertLogMapper.insert(alert);
                    lowCount++;
                }
            }
            log.info("产品安全库存预警检查完成，发现 {} 条", lowCount);
        } catch (Exception e) {
            log.warn("产品安全库存预警检查失败: {}", e.getMessage());
        }
    }

    @Override
    public void checkSafeStockAlert() {
        log.info("检查安全库存预警");
        List<InventoryStock> lowStock = stockMapper.selectLowStock();
        for (InventoryStock stock : lowStock) {
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 当前库存: " + stock.getTotalQuantity() + ", 低于安全库存";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("safe_stock");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setSafeStock(stock.getSafeStock() != null ? stock.getSafeStock() : stock.getTotalQuantity()); // 真实安全库存从物料表取
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!lowStock.isEmpty()) eventPublisher.fire("stock.low", Map.of("count", String.valueOf(lowStock.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("安全库存预警检查完成，发现 {} 条", lowStock.size());
    }


    @Override
    public void checkSafeStockAlert(Long materialId) {
        log.info("检查单物料安全库存预警: materialId={}", materialId);
        InventoryStock stock = stockMapper.selectByMaterialId(materialId);
        if (stock == null) return;

        java.math.BigDecimal safe = java.math.BigDecimal.ZERO;
        try {
            String sql = "SELECT safe_stock FROM inventory_material WHERE material_id = " + materialId;
            java.util.List<java.util.Map<String,Object>> rows = java.util.Collections.emptyList();
            // 使用 MyBatis-Plus 的 selectMaps 搭配 QueryWrapper 需要指定类型
            // 通过 stockMapper 的现有方法查询
            if (stockMapper.selectByMaterialId(materialId) != null) {
                var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InventoryStock>();
                qw.select("safe_stock").eq("material_id", materialId);
                rows = stockMapper.selectMaps(qw);
            }
            if (!rows.isEmpty() && rows.get(0).get("safe_stock") != null)
                safe = new java.math.BigDecimal(rows.get(0).get("safe_stock").toString());
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

        String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                + " 当前可用: " + availableQty + ", 安全库存: " + safe + ", 低于安全库存";
        log.warn(msg);

        InventoryAlertLog alert = new InventoryAlertLog();
        alert.setAlertType("safe_stock");
        alert.setAlertLevel("warning");
        alert.setMaterialId(stock.getMaterialId());
        alert.setMaterialCode(stock.getMaterialCode());
        alert.setMaterialName(stock.getMaterialName());
        alert.setCurrentStock(stock.getTotalQuantity());
        alert.setSafeStock(safe);
        alert.setAlertMessage(msg);
        alert.setAlertTime(java.time.LocalDateTime.now());
        alertLogMapper.insert(alert);

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
        try { if (!overStock.isEmpty()) eventPublisher.fire("stock.max", Map.of("count", String.valueOf(overStock.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
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
        InventoryAlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null) {
            log.error("预警不存在: alertId={}", alertId);
            return false;
        }

        alert.setStatus(1);
        return alertLogMapper.updateById(alert) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchMarkRead(List<Long> alertIds) {
        if (alertIds == null || alertIds.isEmpty()) {
            return false;
        }

        List<InventoryAlertLog> alerts = alertLogMapper.selectBatchIds(alertIds);
        for (InventoryAlertLog alert : alerts) {
            alert.setStatus(1);
        }

        return updateBatchById(alerts);
    }

    @Override
    @Event(value = "inventory.alert.processed", bizId = "#alertId", bizType = "'inventory'")
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
        return alertLogMapper.updateById(alert) > 0;
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
            try {
                com.jjx.inventory.domain.InventoryMaterial mat = materialMapper.selectById(stock.getMaterialId());
                if (mat != null && mat.getMaxStock() != null) {
                    maxStock = mat.getMaxStock();
                }
                if (mat != null && mat.getReorderPoint() != null && mat.getReorderPoint().compareTo(BigDecimal.ZERO) > 0) {
                    reorderPoint = mat.getReorderPoint();
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

            suggestions.add(Map.of(
                    "materialId", stock.getMaterialId(),
                    "materialCode", stock.getMaterialCode(),
                    "materialName", stock.getMaterialName(),
                    "currentStock", stock.getTotalQuantity() != null ? stock.getTotalQuantity().doubleValue() : 0,
                    "suggestQuantity", suggestQty.doubleValue(),
                    "reason", reason,
                    "priority", priority,
                    "sourceAlertId", alertId
            ));
        }

        // 来源2：未处理的订单缺料预警（DEV-573 8-04 衔接齐套检查）
        List<InventoryAlertLog> shortageAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getAlertType, "order_shortage")
                        .eq(InventoryAlertLog::getStatus, 0));
        for (InventoryAlertLog alert : shortageAlerts) {
            // 缺口 = 需求 - 可用，建议补货量取缺口（从 alertMessage 冗余在 suggestion 中，优先解析 suggestion）
            BigDecimal gap = BigDecimal.ZERO;
            if (alert.getSuggestion() != null && alert.getSuggestion().startsWith("建议补货 ")) {
                try {
                    gap = new BigDecimal(alert.getSuggestion().substring("建议补货 ".length()).trim());
                } catch (Exception e) { /* fallthrough */ }
            }
            if (gap.compareTo(BigDecimal.ZERO) <= 0) continue;

            suggestions.add(Map.of(
                    "materialId", alert.getMaterialId(),
                    "materialCode", alert.getMaterialCode(),
                    "materialName", alert.getMaterialName(),
                    "currentStock", alert.getCurrentStock() != null ? alert.getCurrentStock().doubleValue() : 0,
                    "suggestQuantity", gap.doubleValue(),
                    "reason", "订单[" + (alert.getOrderNo() != null ? alert.getOrderNo() : "") + "]缺料，建议补货",
                    "priority", "urgent",
                    "sourceAlertId", alert.getAlertId()
            ));
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
                            .eq(InventoryAlertLog::getStatus, 0)
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
    public boolean batchProcessAlert(java.util.List<Long> alertIds, String relatedOrderNo, String remark) {
        if (alertIds == null || alertIds.isEmpty()) {
            return true;
        }
        String user;
        try {
            user = SecurityUtils.getUsername();
        } catch (Exception e) {
            user = "system";
        }
        int processed = 0;
        for (Long alertId : alertIds) {
            try {
                InventoryAlertLog alert = alertLogMapper.selectById(alertId);
                if (alert == null || java.util.Objects.equals(alert.getStatus(), 2)) {
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
        List<InventoryAlertLog> alerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getStatus, 0)
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
