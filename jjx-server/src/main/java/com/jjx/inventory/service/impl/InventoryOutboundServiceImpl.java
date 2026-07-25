package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryOutboundItem;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.OutboundQueryDTO;
import com.jjx.inventory.dto.vo.OutboundVO;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.mapper.InventoryOutboundItemMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.InventoryOutboundService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jjx.common.exception.BusinessException;

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
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    private final com.jjx.product.mapper.ProductBomMapper productBomMapper;
    private final com.jjx.product.mapper.ProductBomItemMapper productBomItemMapper;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;
    private final com.jjx.sales.mapper.SalesOrderProductMapper salesOrderProductMapper;

    @Override
    public IPage<OutboundVO> page(OutboundQueryDTO query) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getOutboundId() != null) wrapper.eq(InventoryOutboundOrder::getOutboundId, query.getOutboundId());
        if (query.getOutboundNo() != null && !query.getOutboundNo().isEmpty()) wrapper.like(InventoryOutboundOrder::getOutboundNo, query.getOutboundNo());
        if (query.getOutboundType() != null && !query.getOutboundType().isEmpty()) wrapper.eq(InventoryOutboundOrder::getOutboundType, query.getOutboundType());
        if (query.getWarehouseId() != null) wrapper.eq(InventoryOutboundOrder::getWarehouseId, query.getWarehouseId());
        if (query.getSourceType() != null && !query.getSourceType().isEmpty()) wrapper.eq(InventoryOutboundOrder::getSourceType, query.getSourceType());
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
        order.setOrderStatus("pending");
        outboundOrderMapper.insert(order);
        return order.getOutboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(Long outboundId, Long operatorId, String operatorName) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!"pending".equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法确认: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus("approved");
        outboundOrderMapper.updateById(order);
        // 执行库存扣减（复用审批中的库存逻辑）
        approve(outboundId, operatorId, operatorName, "直接确认出库");
        order.setOrderStatus("completed");
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long outboundId, String reason) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if ("completed".equals(order.getOrderStatus())) {
            log.error("已完成的出库单无法取消: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus("cancelled");
        order.setRemark(reason);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean submitApprove(Long outboundId) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus("pending_approval");
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long outboundId, Long approverId, String approverName, String remark) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!"pending_approval".equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法审批: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        // 执行库存扣减
        List<InventoryOutboundItem> items = outboundItemMapper.selectByOutboundId(outboundId);
        for (InventoryOutboundItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal remaining = item.getQuantity();
            // 按FIFO顺序扣减
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

            // 刷新库存汇总
            stockMapper.refreshSummary(item.getMaterialId());

            // 记录流水
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
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getAmount());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(SecurityUtils.getUserId());
            tx.setOperatorName(SecurityUtils.getUsername());
            tx.setRemark("出库审批通过");
            transactionMapper.insert(tx);
        }

        order.setOrderStatus("approved");
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean reject(Long outboundId, Long approverId, String approverName, String remark) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!"pending_approval".equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法驳回: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.REJECTED.getCode());
        order.setRemark(remark);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
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
        LambdaQueryWrapper<com.jjx.product.domain.entity.ProductBom> bomWrapper =
                new LambdaQueryWrapper<com.jjx.product.domain.entity.ProductBom>()
                        .eq(com.jjx.product.domain.entity.ProductBom::getProductId, prodOrder.getProductId())
                        .eq(com.jjx.product.domain.entity.ProductBom::getIsCurrent, 1)
                        .eq(com.jjx.product.domain.entity.ProductBom::getApproveStatus, 1)
                        .orderByDesc(com.jjx.product.domain.entity.ProductBom::getCreateTime)
                        .last("LIMIT 1");
        com.jjx.product.domain.entity.ProductBom bom = productBomMapper.selectOne(bomWrapper);
        if (bom == null) {
            log.warn("生产工单{}的产品{}无生效BOM，跳过自动领料", workOrderId, prodOrder.getProductCode());
            return null;
        }

        // 3. 查询BOM明细
        LambdaQueryWrapper<com.jjx.product.domain.entity.ProductBomItem> itemWrapper =
                new LambdaQueryWrapper<com.jjx.product.domain.entity.ProductBomItem>()
                        .eq(com.jjx.product.domain.entity.ProductBomItem::getBomId, bom.getBomId());
        List<com.jjx.product.domain.entity.ProductBomItem> bomItems = productBomItemMapper.selectList(itemWrapper);
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
        order.setOutboundType("PRODUCTION_PICK");
        order.setSourceType("PRODUCTION");
        order.setSourceId(workOrderId);
        order.setSourceNo(prodOrder.getOrderNo());
        order.setOrderStatus("draft");
        outboundOrderMapper.insert(order);

        // 5. 创建出库单明细
        int sort = 1;
        for (com.jjx.product.domain.entity.ProductBomItem bomItem : bomItems) {
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
            outItem.setQuantity(qtyNeeded);
            outItem.setSortOrder(sort++);
            outboundItemMapper.insert(outItem);
        }

        // 6. 提交审批并自动审批
        order.setOrderStatus("pending_approval");
        outboundOrderMapper.updateById(order);
        approve(order.getOutboundId(), null, null, "生产自动领料");

        log.info("生产领料完成: workOrderId={}, outboundId={}", workOrderId, order.getOutboundId());
        return order.getOutboundId();
    }

    @Override
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
        order.setOrderStatus("draft");
        outboundOrderMapper.insert(order);

        // 4. 创建出库单明细
        int sort = 1;
        for (com.jjx.sales.domain.entity.SalesOrderProduct product : products) {
            InventoryOutboundItem outItem = new InventoryOutboundItem();
            outItem.setOutboundId(order.getOutboundId());
            outItem.setMaterialId(product.getProductId());
            outItem.setMaterialCode(product.getProductCode());
            outItem.setMaterialName(product.getProductName());
            outItem.setQuantity(BigDecimal.valueOf(product.getQuantity()));
            outItem.setUnitPrice(product.getUnitPrice());
            outItem.setSortOrder(sort++);
            outboundItemMapper.insert(outItem);
        }

        // 5. 提交审批并自动审批
        order.setOrderStatus("pending_approval");
        outboundOrderMapper.updateById(order);
        approve(order.getOutboundId(), null, null, "销售发货出库");

        log.info("销售发货出库完成: salesOrderId={}, outboundId={}", salesOrderId, order.getOutboundId());
        return order.getOutboundId();
    }

    @Override
    public List<OutboundVO> getPendingApproval() {
        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryOutboundOrder>()
                        .eq(InventoryOutboundOrder::getOrderStatus, "pending_approval")
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
    public boolean updateStatus(Long outboundId, String status) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
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
        vo.setOutboundId(order.getOutboundId());
        vo.setOutboundNo(order.getOutboundNo());
        vo.setOutboundType(order.getOutboundType());
        vo.setWarehouseId(order.getWarehouseId());
        vo.setSourceType(order.getSourceType());
        vo.setSourceId(order.getSourceId());
        vo.setSourceNo(order.getSourceNo());
        vo.setCustomerId(order.getCustomerId());
        vo.setOutboundDate(order.getOutboundDate());
        vo.setTotalQuantity(order.getTotalQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setApproveStatus(order.getApproveStatus());
        vo.setRemark(order.getRemark());

        // 处理createBy和updateBy，尝试转换为Long


        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        // 设置类型名称
        vo.setOutboundTypeName(getOutboundTypeName(order.getOutboundType()));
        vo.setSourceTypeName(getSourceTypeName(order.getSourceType()));
        vo.setOrderStatusName(getOrderStatusName(order.getOrderStatus()));
        vo.setApproveStatusName(getApproveStatusName(order.getApproveStatus()));

        return vo;
    }

    private static String getOutboundTypeName(String outboundType) {
        if (outboundType == null) {
            return "";
        }
        switch (outboundType) {
            case "production": return "生产领料";
            case "sales": return "销售出库";
            case "return": return "退货出库";
            case "transfer": return "调拨出库";
            case "other": return "其他出库";
            default: return outboundType;
        }
    }

    private static String getSourceTypeName(String sourceType) {
        if (sourceType == null) {
            return "";
        }
        switch (sourceType) {
            case "work_order": return "工单";
            case "sales_order": return "销售订单";
            case "purchase_return": return "采购退货";
            case "transfer_order": return "调拨单";
            default: return sourceType;
        }
    }

    private static String getOrderStatusName(String orderStatus) {
        if (orderStatus == null) {
            return "";
        }
        switch (orderStatus) {
            case "pending": return "待确认";
            case "pending_approval": return "待审批";
            case "approved": return "已审批";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            case "rejected": return "已驳回";
            default: return orderStatus;
        }
    }

    private static String getApproveStatusName(String approveStatus) {
        if (approveStatus == null) {
            return "";
        }
        switch (approveStatus) {
            case "pending": return "待审批";
            case "approved": return "已通过";
            case "rejected": return "已驳回";
            default: return approveStatus;
        }
    }
}
