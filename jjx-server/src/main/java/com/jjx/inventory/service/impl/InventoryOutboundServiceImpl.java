package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryOutboundItem;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.vo.OutboundItemVO;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.OutboundQueryDTO;
import com.jjx.inventory.dto.vo.OutboundVO;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.enums.OutboundTypeEnum;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryOutboundItemMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.service.InventoryOutboundService;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.annotation.Event;

/**
 * 出库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryOutboundServiceImpl extends ServiceImpl<InventoryOutboundOrderMapper, InventoryOutboundOrder>
        implements InventoryOutboundService {

    private final InventoryOutboundOrderMapper outboundOrderMapper;
    private final InventoryOutboundItemMapper outboundItemMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryWarehouseMapper outboundWarehouseMapper;
    private final InventoryMaterialMapper materialMapper;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    private final com.jjx.product.mapper.EngineeringBomMapper productBomMapper;
    private final com.jjx.product.mapper.EngineeringBomItemMapper productBomItemMapper;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;
    private final InventoryAlertService alertService;
    private final com.jjx.sales.mapper.SalesOrderProductMapper salesOrderProductMapper;
    private final com.jjx.inventory.service.OrderStockReserveService orderStockReserveService;

    @Override
    public IPage<OutboundVO> page(OutboundQueryDTO query) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getOutboundId() != null) wrapper.eq(InventoryOutboundOrder::getOutboundId, query.getOutboundId());
        if (query.getOutboundNo() != null && !query.getOutboundNo().isEmpty()) wrapper.like(InventoryOutboundOrder::getOutboundNo, query.getOutboundNo());
        if (query.getOutboundType() != null && !query.getOutboundType().isEmpty()) wrapper.eq(InventoryOutboundOrder::getOutboundType, query.getOutboundType());
        if (query.getWarehouseId() != null) wrapper.eq(InventoryOutboundOrder::getWarehouseId, query.getWarehouseId());
        if (query.getSourceType() != null && !query.getSourceType().isEmpty()) wrapper.eq(InventoryOutboundOrder::getSourceType, query.getSourceType());
        if (query.getSourceTypeNe() != null && !query.getSourceTypeNe().isEmpty()) wrapper.ne(InventoryOutboundOrder::getSourceType, query.getSourceTypeNe());
        if (query.getSourceNo() != null && !query.getSourceNo().isEmpty()) wrapper.like(InventoryOutboundOrder::getSourceNo, query.getSourceNo());
        if (query.getOrderStatus() != null && !query.getOrderStatus().isEmpty()) wrapper.eq(InventoryOutboundOrder::getOrderStatus, query.getOrderStatus());
        if (query.getApproveStatus() != null && !query.getApproveStatus().isEmpty()) wrapper.eq(InventoryOutboundOrder::getApproveStatus, query.getApproveStatus());
        if (query.getOutboundDateStart() != null) wrapper.ge(InventoryOutboundOrder::getOutboundDate, query.getOutboundDateStart());
        if (query.getOutboundDateEnd() != null) wrapper.le(InventoryOutboundOrder::getOutboundDate, query.getOutboundDateEnd());
        if (query.getCreateTimeStart() != null && !query.getCreateTimeStart().isEmpty()) wrapper.ge(InventoryOutboundOrder::getCreateTime, query.getCreateTimeStart());
        if (query.getCreateTimeEnd() != null && !query.getCreateTimeEnd().isEmpty()) wrapper.le(InventoryOutboundOrder::getCreateTime, query.getCreateTimeEnd());
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            boolean isAsc = "asc".equalsIgnoreCase(query.getOrderDirection());
            switch (query.getOrderBy()) {
                case "outboundNo": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getOutboundNo); break;
                case "outboundDate": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getOutboundDate); break;
                case "createTime": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getCreateTime); break;
                case "totalAmount": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getTotalAmount); break;
                default: wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);
            }
        } else wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        Page<InventoryOutboundOrder> orderPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryOutboundOrder> orderResult = outboundOrderMapper.selectPage(orderPage, wrapper);
        Page<OutboundVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(orderResult.getTotal());
        voPage.setPages(orderResult.getPages());
        voPage.setRecords(convertToVOList(orderResult.getRecords()));
        return voPage;
    }

    @Override
    public OutboundVO getDetail(Long outboundId) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return null;
        }
        OutboundVO vo = convertToVO(order);
        List<InventoryOutboundItem> items = outboundItemMapper.selectByOutboundId(outboundId);
        if (items != null && !items.isEmpty()) {
            vo.setItems(convertToItemVOList(items));
        }
        return vo;
    }

    @Override
    @Event(value = "inventory.outbound.created", bizId = "#params", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        log.info("创建出库单: {}", params);
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo((String) params.getOrDefault("outboundNo", "OUT-" + System.currentTimeMillis()));
        order.setOutboundType((String) params.getOrDefault("outboundType", "sales"));
        order.setSourceType((String) params.get("sourceType"));
        if (params.get("sourceId") != null) order.setSourceId(Long.valueOf(params.get("sourceId").toString()));
        order.setSourceNo((String) params.get("sourceNo"));
        if (params.get("warehouseId") != null) order.setWarehouseId(Long.valueOf(params.get("warehouseId").toString()));
        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        outboundOrderMapper.insert(order);
        return order.getOutboundId();
    }

    @Override
    @Event(value = "inventory.outbound.confirmed", bizId = "#outboundId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(Long outboundId, Long operatorId, String operatorName) {
        // DEV-651 方案A：行锁查询，锁住单据行直到事务提交，并发下第二个请求阻塞后状态校验失败，杜绝重复出入库
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())
                && !OrderStatusEnum.APPROVED.getCode().equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法确认: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        // 库存操作统一发生在 confirm（DEV-651：confirm=审批+完成 单路径，approve 不再动库存）
        // 直接执行库存扣减（不经过 approve，避免状态不匹配）
        List<InventoryOutboundItem> outItems = outboundItemMapper.selectByOutboundId(outboundId);
        // DEV-580：销售发货出库时，先同步释放该订单的成品预留（扣减前释放，FIFO才能扣到预留部分）
        if ("SALES".equals(order.getSourceType()) && order.getSourceId() != null) {
            for (InventoryOutboundItem item : outItems) {
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                try {
                    orderStockReserveService.releaseForOutbound(order.getSourceId(), item.getMaterialId(), item.getQuantity());
                } catch (Exception e) {
                    log.warn("出库联动释放成品预留失败（不影响扣减）: {}", e.getMessage());
                }
            }
        }
        for (InventoryOutboundItem item : outItems) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal remaining = item.getQuantity();
            List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(item.getMaterialId());
            for (InventoryStockItem si : fifoItems) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal deductQty = remaining.min(si.getQuantity().subtract(si.getReservedQuantity()));
                if (deductQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                stockItemMapper.deductStock(si.getItemId(), deductQty);
                remaining = remaining.subtract(deductQty);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("物料[" + item.getMaterialCode() + "]库存不足，缺少: " + remaining);
            }
            stockMapper.refreshSummary(item.getMaterialId());
            // 获取扣减前的库存汇总
            java.math.BigDecimal beforeQty = java.math.BigDecimal.ZERO;
            InventoryStock currentStock = stockMapper.selectByMaterialId(item.getMaterialId());
            if (currentStock != null && currentStock.getTotalQuantity() != null) {
                beforeQty = currentStock.getTotalQuantity().add(item.getQuantity());
            }
            InventoryTransaction tx = new InventoryTransaction();
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getWarehouseId());
            tx.setLocationId(item.getLocationId());
            tx.setTransactionType("OUTBOUND");
            tx.setSourceType(order.getSourceType());
            tx.setSourceId(outboundId);
            tx.setSourceNo(order.getOutboundNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(item.getQuantity().negate());
            tx.setBeforeQuantity(beforeQty);
            tx.setAfterQuantity(beforeQty.subtract(item.getQuantity()));
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getAmount());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            tx.setRemark("出库确认完成");
            transactionMapper.insert(tx);
        }
        // 安全库存检查（移到循环外只调一次，原实现在循环内全量重复执行）
        try {
            alertService.checkSafeStockAlert();
        } catch (Exception e) {
            log.warn("安全库存检查失败: {}", e.getMessage());
        }
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
        boolean updated = outboundOrderMapper.updateById(order) > 0;

        // 生产领料单确认发料后，同步更新工单领料状态为已领料(2)
        try {
            if ("work_order".equals(order.getSourceType()) && order.getSourceId() != null) {
                com.jjx.production.domain.entity.ProductionOrder prodOrder =
                        productionOrderMapper.selectById(order.getSourceId());
                if (prodOrder != null && prodOrder.getMaterialStatus() != null
                        && prodOrder.getMaterialStatus() < 2) {
                    prodOrder.setMaterialStatus(2);
                    productionOrderMapper.updateById(prodOrder);
                }
            }
        } catch (Exception e) {
            log.warn("确认发料后更新工单领料状态失败: {}", e.getMessage());
        }

        return updated;
    }

    @Override
    @Event(value = "inventory.outbound.cancelled", bizId = "#outboundId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long outboundId, String reason) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (OrderStatusEnum.COMPLETED.getCode().equals(order.getOrderStatus())) {
            log.error("已完成的出库单无法取消: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
        order.setRemark(reason);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.outbound.submitted", bizId = "#outboundId", bizType = "'inventory'")
    public boolean submitApprove(Long outboundId) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        // DEV-651：只有草稿/已驳回/已取消状态的单才能提交审批，防止把已完成/进行中的单打回待审批
        Integer status = order.getOrderStatus();
        if (!OrderStatusEnum.DRAFT.getCode().equals(status)
                && !OrderStatusEnum.REJECTED.getCode().equals(status)
                && !OrderStatusEnum.CANCELLED.getCode().equals(status)) {
            log.error("出库单状态不允许提交审批: outboundId={}, status={}", outboundId, status);
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.outbound.approved", bizId = "#outboundId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long outboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法审批: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        // DEV-651：审批只做状态流转，库存扣减统一由 confirm 执行（confirm=审批+完成 单路径）
        // 原实现在这里扣库存，导致：①与 confirm 重复维护扣减逻辑；②approve 后无出口到 COMPLETED，单子卡死
        order.setOrderStatus(OrderStatusEnum.APPROVED.getCode());
        if (remark != null) {
            order.setApproveRemark(remark);
        }
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.outbound.rejected", bizId = "#outboundId", bizType = "'inventory'")
    public boolean reject(Long outboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法驳回: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.REJECTED.getCode());
        order.setRemark(remark);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.outbound.created_from_production", bizId = "#workOrderId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public Long createFromProduction(Long workOrderId) {
        log.info("从生产工单创建出库单: workOrderId={}", workOrderId);

        // 1. 查询生产工单
        com.jjx.production.domain.entity.ProductionOrder prodOrder =
                productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }

        // 2. 查询当前生效BOM
        LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom> bomWrapper =
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, prodOrder.getProductId())
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, 1)
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getApproveStatus, 3)
                        .orderByDesc(com.jjx.engineering.domain.entity.EngineeringBom::getCreateTime)
                        .last("LIMIT 1");
        com.jjx.engineering.domain.entity.EngineeringBom bom = productBomMapper.selectOne(bomWrapper);
        if (bom == null) {
            log.warn("生产工单{}的产品{}无生效BOM，跳过自动领料", workOrderId, prodOrder.getProductCode());
            return null;
        }

        // 3. 查询BOM明细
        LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBomItem> itemWrapper =
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBomItem>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBomItem::getBomId, bom.getBomId());
        List<com.jjx.engineering.domain.entity.EngineeringBomItem> bomItems = productBomItemMapper.selectList(itemWrapper);
        if (bomItems.isEmpty()) {
            log.warn("BOM{}无明细，无法自动领料", bom.getBomCode());
            return null;
        }

        // 4. 创建出库单
        String outboundNo = "PICK-" + prodOrder.getOrderNo();
        // 检查是否已生成
        LambdaQueryWrapper<InventoryOutboundOrder> existCheck = new LambdaQueryWrapper<InventoryOutboundOrder>()
                .eq(InventoryOutboundOrder::getOutboundNo, outboundNo);
        if (outboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("生产工单{}的领料单已存在", workOrderId);
            return null;
        }

        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo(outboundNo);
        order.setOutboundType(OutboundTypeEnum.PRODUCTION.getCode());
        order.setSourceType("work_order");
        order.setSourceId(workOrderId);
        order.setSourceNo(prodOrder.getOrderNo());
        order.setTraceId(prodOrder.getTraceId()); // 链路追踪（DEV-568）：工单→领料出库单继承
        order.setOutboundDate(LocalDate.now());
        // 默认取第一个启用仓库（工单无仓库字段）
        try {
            InventoryWarehouse defaultWh = outboundWarehouseMapper.selectOne(
                    new LambdaQueryWrapper<InventoryWarehouse>()
                            .eq(InventoryWarehouse::getStatus, 1)
                            .orderByAsc(InventoryWarehouse::getWarehouseId)
                            .last("LIMIT 1"));
            if (defaultWh != null) {
                order.setWarehouseId(defaultWh.getWarehouseId());
            }
        } catch (Exception e) {
            log.warn("获取默认仓库失败: {}", e.getMessage());
        }
        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        outboundOrderMapper.insert(order);

        // 5. 创建出库单明细
        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        // DEV-651：生成领料单前先做库存预检，不足则抛异常（由 startOrder 捕获记录，避免"工单已开工但领料失败"无人知晓）
        List<String> shortageMsgs = new java.util.ArrayList<>();
        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;

            BigDecimal baseQty = bomItem.getBaseQty() != null && bomItem.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                    ? bomItem.getBaseQty() : BigDecimal.ONE;
            BigDecimal qtyNeeded = bomItem.getQuantity()
                    .multiply(prodOrder.getPlannedQuantity())
                    .divide(baseQty, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(
                            bomItem.getLossRate() != null ? BigDecimal.valueOf(bomItem.getLossRate()).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO
                    ))
                    .setScale(0, java.math.RoundingMode.UP);

            // 库存预检：按 FIFO 可用量（批次数量-预留）汇总对比
            BigDecimal available = BigDecimal.ZERO;
            try {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(bomItem.getMaterialId());
                for (InventoryStockItem si : fifoItems) {
                    available = available.add(si.getQuantity().subtract(si.getReservedQuantity()));
                }
            } catch (Exception e) {
                log.warn("领料预检查询库存失败: materialId={}, err={}", bomItem.getMaterialId(), e.getMessage());
            }
            if (available.compareTo(qtyNeeded) < 0) {
                shortageMsgs.add("物料[" + bomItem.getMaterialCode() + "]需" + qtyNeeded + ", 可用" + available);
            }
        }
        if (!shortageMsgs.isEmpty()) {
            String msg = "库存不足，无法生成领料单: " + String.join("; ", shortageMsgs);
            log.error("生产工单{}领料预检失败: {}", workOrderId, msg);
            throw new BusinessException(msg);
        }

        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;

            BigDecimal baseQty = bomItem.getBaseQty() != null && bomItem.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                    ? bomItem.getBaseQty() : BigDecimal.ONE;
            BigDecimal qtyNeeded = bomItem.getQuantity()
                    .multiply(prodOrder.getPlannedQuantity())
                    .divide(baseQty, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(
                            bomItem.getLossRate() != null ? BigDecimal.valueOf(bomItem.getLossRate()).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO
                    ))
                    .setScale(0, java.math.RoundingMode.UP);

            InventoryOutboundItem outItem = new InventoryOutboundItem();
            outItem.setOutboundId(order.getOutboundId());
            outItem.setMaterialId(bomItem.getMaterialId());
            outItem.setMaterialCode(bomItem.getMaterialCode());
            outItem.setMaterialName(bomItem.getMaterialName());
            outItem.setSpecification(bomItem.getSpecification());
            outItem.setUnit(bomItem.getUnit());
            outItem.setQuantity(qtyNeeded);
            outItem.setSortOrder(sort++);
            totalQty = totalQty.add(qtyNeeded);
            outboundItemMapper.insert(outItem);
        }

        // 6. 汇总并更新工单领料状态（待发料）
        order.setTotalQuantity(totalQty);
        outboundOrderMapper.updateById(order);
        prodOrder.setMaterialStatus(1);
        productionOrderMapper.updateById(prodOrder);

        log.info("生产领料单已生成(待发料): workOrderId={}, outboundId={}", workOrderId, order.getOutboundId());
        return order.getOutboundId();
    }

    @Override
    @Event(value = "inventory.outbound.created_from_sales", bizId = "#salesOrderId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public Long createFromSales(Long salesOrderId) {
        log.info("从销售订单创建出库单: salesOrderId={}", salesOrderId);

        // 1. 查询销售订单
        com.jjx.sales.domain.entity.SalesOrder salesOrder = salesOrderMapper.selectById(salesOrderId);
        if (salesOrder == null) {
            throw new BusinessException("销售订单不存在: " + salesOrderId);
        }

        // 2. 查询订单产品
        LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesOrderProduct> itemWrapper =
                new LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesOrderProduct>()
                        .eq(com.jjx.sales.domain.entity.SalesOrderProduct::getOrderId, salesOrderId);
        List<com.jjx.sales.domain.entity.SalesOrderProduct> products = salesOrderProductMapper.selectList(itemWrapper);
        if (products.isEmpty()) {
            throw new BusinessException("销售订单无产品明细，无法出库");
        }

        // 3. 创建出库单
        String outboundNo = "SHIP-" + salesOrder.getOrderNo();
        LambdaQueryWrapper<InventoryOutboundOrder> existCheck = new LambdaQueryWrapper<InventoryOutboundOrder>()
                .eq(InventoryOutboundOrder::getOutboundNo, outboundNo);
        if (outboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("销售订单{}的发货出库单已存在", salesOrderId);
            return null;
        }

        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo(outboundNo);
        order.setOutboundType("SALES_SHIP");
        order.setSourceType("SALES");
        order.setSourceId(salesOrderId);
        order.setSourceNo(salesOrder.getOrderNo());
        order.setTraceId(salesOrder.getTraceId()); // 链路追踪（DEV-568）：销售订单→发货出库单继承
        order.setOutboundDate(LocalDate.now());
        order.setOrderStatus(OrderStatusEnum.DRAFT.getCode());
        outboundOrderMapper.insert(order);

        // 4. 创建出库单明细（成品物料映射：产品→inventory_material.product_id）
        int sort = 1;
        for (com.jjx.sales.domain.entity.SalesOrderProduct product : products) {
            InventoryOutboundItem outItem = new InventoryOutboundItem();
            // 查成品物料档案（发布时自动创建，material_type='F'）
            com.jjx.inventory.domain.InventoryMaterial finishMat = null;
            try {
                finishMat = materialMapper.selectOne(
                        new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                .eq(com.jjx.inventory.domain.InventoryMaterial::getProductId, product.getProductId())
                                .last("LIMIT 1"));
            } catch (Exception e) {
                log.warn("查询成品物料失败: {}", e.getMessage());
            }
            if (finishMat == null) {
                throw new BusinessException("产品[" + product.getProductCode() + "]无成品物料档案，请先发布产品");
            }
            outItem.setOutboundId(order.getOutboundId());
            outItem.setMaterialId(finishMat.getMaterialId());
            outItem.setMaterialCode(finishMat.getMaterialCode());
            outItem.setMaterialName(finishMat.getMaterialName());
            outItem.setQuantity(BigDecimal.valueOf(product.getQuantity()));
            outItem.setUnitPrice(product.getUnitPrice());
            outItem.setSortOrder(sort++);
            outboundItemMapper.insert(outItem);
        }

        // 5. 提交审批并自动审批
        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        outboundOrderMapper.updateById(order);
        approve(order.getOutboundId(), null, null, "销售发货出库");

        log.info("销售发货出库完成: salesOrderId={}, outboundId={}", salesOrderId, order.getOutboundId());
        return order.getOutboundId();
    }

    @Override
    public List<OutboundVO> getPendingApproval() {
        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryOutboundOrder>()
                        .eq(InventoryOutboundOrder::getOrderStatus, OrderStatusEnum.PENDING.getCode())
                        .orderByAsc(InventoryOutboundOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<OutboundVO> getByDateRange(String startDate, String endDate) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryOutboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryOutboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(wrapper);
        return convertToVOList(orders);
    }

    @Override
    public OutboundVO getBySource(String sourceType, Long sourceId) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryOutboundOrder::getSourceType, sourceType)
                .eq(InventoryOutboundOrder::getSourceId, sourceId);
        InventoryOutboundOrder order = outboundOrderMapper.selectOne(wrapper);
        return convertToVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long outboundId, Integer status) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus(status);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryOutboundOrder> pageQuery(Map<String, Object> params) {
        String outboundNo = (String) params.get("outboundNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (outboundNo != null && !outboundNo.isEmpty()) {
            wrapper.like(InventoryOutboundOrder::getOutboundNo, outboundNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryOutboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryOutboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        Page<InventoryOutboundOrder> page = new Page<>(pageNum, pageSize);
        return outboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        if (params != null && params.get("outboundId") != null) {
            Long outboundId = Long.valueOf(params.get("outboundId").toString());
            OutboundVO detail = getDetail(outboundId);
            if (detail != null) {
                return Map.of("code", 200, "data", detail);
            }
        }
        return Map.of("code", 404, "message", "出库单不存在");
    }

    private static List<OutboundVO> convertToVOList(List<InventoryOutboundOrder> orders) {
        List<OutboundVO> result = new ArrayList<>();
        for (InventoryOutboundOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static OutboundVO convertToVO(InventoryOutboundOrder order) {
        if (order == null) {
            return null;
        }

        OutboundVO vo = new OutboundVO();
        BeanUtils.copyProperties(order, vo);

        // 设置类型名称与状态名称
        OutboundTypeEnum typeEnum = OutboundTypeEnum.getByCode(order.getOutboundType());
        vo.setOutboundTypeName(typeEnum != null ? typeEnum.getLabel() : order.getOutboundType());
        OrderStatusEnum statusEnum = OrderStatusEnum.getByCode(order.getOrderStatus());
        vo.setStatus(order.getOrderStatus());
        if (statusEnum != null) {
            // 生产领料单：待审批显示"待发料"，已完成显示"已发料"
            if (OutboundTypeEnum.PRODUCTION.getCode().equals(order.getOutboundType())) {
                if (OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())) {
                    vo.setStatusName("待发料");
                } else if (OrderStatusEnum.COMPLETED.getCode().equals(order.getOrderStatus())) {
                    vo.setStatusName("已发料");
                } else {
                    vo.setStatusName(statusEnum.getLabel());
                }
            } else {
                vo.setStatusName(statusEnum.getLabel());
            }
        }

        return vo;
    }

    /**
     * DEV-660：判断是否领料单（production 类型或 PICK- 单号前缀）
     */
    private static boolean isPickOrder(OutboundVO vo) {
        if (vo == null) return false;
        if (OutboundTypeEnum.PRODUCTION.getCode().equals(vo.getOutboundType())) return true;
        return vo.getOutboundNo() != null && vo.getOutboundNo().startsWith("PICK-");
    }

    private static List<OutboundItemVO> convertToItemVOList(List<InventoryOutboundItem> items) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        List<OutboundItemVO> result = new ArrayList<>();
        for (InventoryOutboundItem item : items) result.add(convertToItemVO(item));
        return result;
    }

    private static OutboundItemVO convertToItemVO(InventoryOutboundItem item) {
        if (item == null) return null;
        OutboundItemVO vo = new OutboundItemVO();
        vo.setOutboundItemId(item.getItemId());
        vo.setOutboundId(item.getOutboundId());
        vo.setMaterialId(item.getMaterialId());
        vo.setMaterialCode(item.getMaterialCode());
        vo.setMaterialName(item.getMaterialName());
        vo.setSpecification(item.getSpecification());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        vo.setBatchNo(item.getBatchNo());
        vo.setLocationId(item.getLocationId());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }

    @Override
    public byte[] exportPdf(Long outboundId) {
        com.jjx.inventory.dto.vo.OutboundVO vo = getDetail(outboundId);
        if (vo == null) {
            throw new BusinessException("出库单不存在: " + outboundId);
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("出库单号", vo.getOutboundNo());
        info.put("出库日期", vo.getOutboundDate() == null ? "" : vo.getOutboundDate().toString());
        info.put("出库类型", vo.getOutboundTypeName() == null ? (vo.getOutboundType() == null ? "-" : vo.getOutboundType()) : vo.getOutboundTypeName());
        info.put("来源单号", vo.getSourceNo() == null ? "-" : vo.getSourceNo());
        info.put("仓库", vo.getWarehouseName() == null ? "-" : vo.getWarehouseName());
        info.put("客户", vo.getCustomerName() == null ? "-" : vo.getCustomerName());
        info.put("状态", vo.getStatusName() == null ? "-" : vo.getStatusName());

        java.util.List<String[]> rows = new java.util.ArrayList<>();
        if (vo.getItems() != null) {
            for (com.jjx.inventory.dto.vo.OutboundItemVO item : vo.getItems()) {
                String spec = item.getMaterialName() == null ? "" : item.getMaterialName();
                if (item.getSpecification() != null && !item.getSpecification().isBlank()) {
                    spec = spec.isBlank() ? item.getSpecification() : spec + " / " + item.getSpecification();
                }
                rows.add(new String[]{
                        String.valueOf(rows.size() + 1),
                        item.getMaterialCode() == null ? "" : item.getMaterialCode(),
                        spec,
                        item.getQuantity() == null ? "" : df.format(item.getQuantity()),
                        item.getUnit() == null ? "" : item.getUnit(),
                        item.getUnitPrice() == null ? "" : df.format(item.getUnitPrice()),
                        item.getAmount() == null ? "" : df.format(item.getAmount()),
                        item.getBatchNo() == null ? "" : item.getBatchNo(),
                });
            }
        }

        return com.jjx.common.utils.pdf.PdfDocBuilder.create()
                // DEV-660：领料单（production 类型/PICK- 单号）打印标题为「领 料 单」，其余「出 库 单」
                .title(isPickOrder(vo) ? "领  料  单" : "出  库  单")
                .info(info)
                .items(new String[]{"序号", "物料编码", "物料名称/规格", "数量", "单位", "单价", "金额", "批次"}, rows)
                .amounts(new String[][]{
                        {"总数量", vo.getTotalQuantity() == null ? "" : df.format(vo.getTotalQuantity())},
                        {"总金额", vo.getTotalAmount() == null ? "" : df.format(vo.getTotalAmount())},
                })
                .remark(vo.getRemark())
                .signatures("仓管员：", "领料人：", "日期：")
                .toBytes();
    }
}
