package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.vo.InboundItemVO;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.mapper.InventoryInboundItemMapper;
import com.jjx.inventory.mapper.InventoryInboundOrderMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.jjx.system.annotation.Event;

/**
 * 入库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryInboundServiceImpl extends ServiceImpl<InventoryInboundOrderMapper, InventoryInboundOrder>
        implements InventoryInboundService {

    private final InventoryInboundOrderMapper inboundOrderMapper;
    private final InventoryInboundItemMapper inboundItemMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final EventPublisher eventPublisher;
    private final InventoryAlertService alertService;

    @Override
    public IPage<InboundVO> page(InboundQueryDTO query) {
        // 构建查询条件
        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();

        // 根据查询参数添加条件
        if (query.getInboundId() != null) {
            wrapper.eq(InventoryInboundOrder::getInboundId, query.getInboundId());
        }

        if (query.getInboundNo() != null && !query.getInboundNo().isEmpty()) {
            wrapper.like(InventoryInboundOrder::getInboundNo, query.getInboundNo());
        }

        if (query.getInboundType() != null && !query.getInboundType().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getInboundType, query.getInboundType());
        }

        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryInboundOrder::getWarehouseId, query.getWarehouseId());
        }

        if (query.getSourceType() != null && !query.getSourceType().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getSourceType, query.getSourceType());
        }

        if (query.getSourceNo() != null && !query.getSourceNo().isEmpty()) {
            wrapper.like(InventoryInboundOrder::getSourceNo, query.getSourceNo());
        }

        if (query.getOrderStatus() != null && !query.getOrderStatus().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getOrderStatus, query.getOrderStatus());
        }

        if (query.getApproveStatus() != null && !query.getApproveStatus().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getApproveStatus, query.getApproveStatus());
        }

        // 入库日期范围查询
        if (query.getInboundDateStart() != null) {
            wrapper.ge(InventoryInboundOrder::getInboundDate, query.getInboundDateStart());
        }

        if (query.getInboundDateEnd() != null) {
            wrapper.le(InventoryInboundOrder::getInboundDate, query.getInboundDateEnd());
        }

        // 创建时间范围查询
        if (query.getCreateTimeStart() != null && !query.getCreateTimeStart().isEmpty()) {
            wrapper.ge(InventoryInboundOrder::getCreateTime, query.getCreateTimeStart());
        }

        if (query.getCreateTimeEnd() != null && !query.getCreateTimeEnd().isEmpty()) {
            wrapper.le(InventoryInboundOrder::getCreateTime, query.getCreateTimeEnd());
        }

        // 排序处理
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            String orderBy = query.getOrderBy();
            boolean isAsc = "asc".equalsIgnoreCase(query.getOrderDirection());

            // 根据orderBy字段映射到实体字段
            switch (orderBy) {
                case "inboundNo":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getInboundNo);
                    break;
                case "inboundDate":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getInboundDate);
                    break;
                case "createTime":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getCreateTime);
                    break;
                case "totalAmount":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getTotalAmount);
                    break;
                default:
                    // 默认按创建时间倒序
                    wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);
            }
        } else {
            // 默认按创建时间倒序
            wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);
        }

        // 执行分页查询
        Page<InventoryInboundOrder> orderPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryInboundOrder> orderResult = inboundOrderMapper.selectPage(orderPage, wrapper);

        // 转换为VO分页
        Page<InboundVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(orderResult.getTotal());
        voPage.setPages(orderResult.getPages());

        // 转换数据
        List<InboundVO> voList = convertToVOList(orderResult.getRecords());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public InboundVO getDetail(Long inboundId) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return null;
        }

        InboundVO vo = convertToVO(order);
        // 获取入库单明细项并设置到VO对象
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
        if (items != null && !items.isEmpty()) {
            vo.setItems(convertToItemVOList(items));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.created", bizId = "#params", bizType = "'inventory'")
    public Long create(Map<String, Object> params) {
        log.info("创建入库单: {}", params);
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo((String) params.getOrDefault("inboundNo", "IN-" + System.currentTimeMillis()));
        order.setInboundType((String) params.getOrDefault("inboundType", "purchase"));
        order.setSourceType((String) params.get("sourceType"));
        if (params.get("sourceId") != null) order.setSourceId(Long.valueOf(params.get("sourceId").toString()));
        order.setSourceNo((String) params.get("sourceNo"));
        if (params.get("warehouseId") != null) order.setWarehouseId(Long.valueOf(params.get("warehouseId").toString()));
        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        inboundOrderMapper.insert(order);
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.confirmed", bizId = "#inboundId", bizType = "'inventory'")
    public boolean confirm(Long inboundId, Long operatorId, String operatorName) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法确认: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.APPROVED.getCode());
        inboundOrderMapper.updateById(order);
        // 执行库存增加（复用审批中的库存逻辑）
        approve(inboundId, operatorId, operatorName, "直接确认入库");
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
        // 安全库存检查
        try {
            List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
            for (InventoryInboundItem item : items) {
                alertService.checkSafeStockAlert(item.getMaterialId());
            }
        } catch (Exception e) {
            log.warn("安全库存检查失败: {}", e.getMessage());
        }
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.cancelled", bizId = "#inboundId", bizType = "'inventory'")
    public boolean cancel(Long inboundId, String reason) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (OrderStatusEnum.COMPLETED.getCode().equals(order.getOrderStatus())) {
            log.error("已完成的入库单无法取消: inboundId={}", inboundId);
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
        order.setRemark(reason);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.inbound.submitted", bizId = "#inboundId", bizType = "'inventory'")
    public boolean submitApprove(Long inboundId) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.approved", bizId = "#inboundId", bizType = "'inventory'")
    public boolean approve(Long inboundId, Long approverId, String approverName, String remark) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法审批: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        // 执行库存增加
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
        for (InventoryInboundItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            // 查找现有批次库存
            LambdaQueryWrapper<InventoryStockItem> wrapper = new LambdaQueryWrapper<InventoryStockItem>()
                    .eq(InventoryStockItem::getMaterialId, item.getMaterialId())
                    .eq(InventoryStockItem::getBatchNo, item.getBatchNo())
                    .eq(InventoryStockItem::getStatus, 1);
            if (item.getLocationId() != null) {
                wrapper.eq(InventoryStockItem::getLocationId, item.getLocationId());
            }
            InventoryStockItem existing = stockItemMapper.selectOne(wrapper);

            if (existing != null) {
                // 已有批次，增加数量
                existing.setQuantity(existing.getQuantity().add(item.getQuantity()));
                existing.setLastInboundTime(LocalDateTime.now());
                stockItemMapper.updateById(existing);
            } else {
                // 新建批次记录
                InventoryStockItem newItem = new InventoryStockItem();
                newItem.setMaterialId(item.getMaterialId());
                newItem.setMaterialCode(item.getMaterialCode());
                newItem.setMaterialName(item.getMaterialName());
                newItem.setWarehouseId(order.getWarehouseId());
                newItem.setLocationId(item.getLocationId());
                newItem.setBatchNo(item.getBatchNo());
                newItem.setProductionDate(item.getProductionDate());
                newItem.setExpiryDate(item.getExpiryDate());
                newItem.setQuantity(item.getQuantity());
                newItem.setReservedQuantity(BigDecimal.ZERO);
                newItem.setUnitCost(item.getUnitPrice());
                newItem.setStatus(1);
                newItem.setLastInboundTime(LocalDateTime.now());
                stockItemMapper.insert(newItem);
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
            tx.setTransactionType("INBOUND");
            tx.setSourceType("PURCHASE");
            tx.setSourceId(inboundId);
            tx.setSourceNo(order.getInboundNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(item.getQuantity());
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getAmount());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(SecurityUtils.getUserId());
            tx.setOperatorName(SecurityUtils.getUsername());
            tx.setRemark("入库审批通过");
            transactionMapper.insert(tx);
        }

        order.setOrderStatus(OrderStatusEnum.APPROVED.getCode());
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.inbound.rejected", bizId = "#inboundId", bizType = "'inventory'")
    public boolean reject(Long inboundId, Long approverId, String approverName, String remark) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法驳回: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.REJECTED.getCode());
        order.setRemark(remark);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.created_from_purchase", bizId = "#purchaseOrderId", bizType = "'inventory'")
    public Long createFromPurchase(Long purchaseOrderId) {
        log.info("从采购订单创建入库单: purchaseOrderId={}", purchaseOrderId);

        // 1. 查询采购订单
        PurchaseOrder po = purchaseOrderMapper.selectById(purchaseOrderId);
        if (po == null) {
            throw new BusinessException("采购订单不存在: " + purchaseOrderId);
        }

        // 2. 检查是否已生成入库单
        String inboundNo = "PO-" + po.getOrderNo();
        LambdaQueryWrapper<InventoryInboundOrder> existCheck = new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getInboundNo, inboundNo);
        if (inboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("采购订单{}的入库单已存在", purchaseOrderId);
            return null;
        }

        // 3. 查询采购订单明细
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByOrderId(purchaseOrderId);
        if (items.isEmpty()) {
            throw new BusinessException("采购订单无物料明细");
        }

        // 4. 创建入库单
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("PURCHASE");
        order.setSourceType("PURCHASE");
        order.setSourceId(purchaseOrderId);
        order.setSourceNo(po.getOrderNo());
        order.setWarehouseId(1L); // 默认仓库
        order.setOrderStatus(OrderStatusEnum.DRAFT.getCode());
        inboundOrderMapper.insert(order);

        // 5. 创建入库单明细
        int sort = 1;
        for (PurchaseOrderItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal receiveQty = item.getQuantity(); // 按采购数量入库
            if (item.getReceivedQuantity() != null) {
                receiveQty = receiveQty.subtract(item.getReceivedQuantity());
            }
            if (receiveQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            InventoryInboundItem inboundItem = new InventoryInboundItem();
            inboundItem.setInboundId(order.getInboundId());
            inboundItem.setMaterialId(item.getMaterialId());
            inboundItem.setMaterialCode(item.getMaterialCode());
            inboundItem.setMaterialName(item.getMaterialName());
            inboundItem.setQuantity(receiveQty);
            inboundItem.setUnitPrice(item.getUnitPrice());
            inboundItem.setAmount(item.getAmount());
            inboundItem.setBatchNo("PO-" + po.getOrderNo() + "-" + sort);
            inboundItem.setSortOrder(sort++);
            inboundItemMapper.insert(inboundItem);

            // 更新采购订单已收数量
            purchaseOrderItemMapper.updateReceivedQuantity(item.getItemId(), receiveQty);
        }

        // 6. 提交审批并自动审批
        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        inboundOrderMapper.updateById(order);
        approve(order.getInboundId(), null, null, "采购到货入库");

        try { eventPublisher.fire("purchase.arrived", Map.of("sourceNo", order.getSourceNo(), "inboundId", String.valueOf(order.getInboundId()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("采购入库完成: purchaseOrderId={}, inboundId={}", purchaseOrderId, order.getInboundId());
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.created_from_production", bizId = "#workOrderId", bizType = "'inventory'")
    public Long createFromProduction(Long workOrderId) {
        log.info("从生产工单创建入库单: workOrderId={}", workOrderId);

        // 1. 查询生产工单
        ProductionOrder prodOrder = productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }

        // 2. 创建入库单
        String inboundNo = "FINISH-" + prodOrder.getOrderNo();
        LambdaQueryWrapper<InventoryInboundOrder> existCheck = new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getInboundNo, inboundNo);
        if (inboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("生产工单{}的完工入库单已存在", workOrderId);
            return null;
        }

        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("PRODUCTION_FINISH");
        order.setSourceType("PRODUCTION");
        order.setSourceId(workOrderId);
        order.setSourceNo(prodOrder.getOrderNo());
        order.setOrderStatus(OrderStatusEnum.DRAFT.getCode());
        inboundOrderMapper.insert(order);

        // 3. 创建入库明细
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setInboundId(order.getInboundId());
        inboundItem.setMaterialId(prodOrder.getProductId());
        inboundItem.setMaterialCode(prodOrder.getProductCode());
        inboundItem.setMaterialName(prodOrder.getProductName());
        inboundItem.setQuantity(prodOrder.getCompletedQuantity() != null
                ? prodOrder.getCompletedQuantity() : prodOrder.getPlannedQuantity());
        inboundItem.setBatchNo("BATCH-" + prodOrder.getOrderNo());
        inboundItem.setSortOrder(1);
        inboundItemMapper.insert(inboundItem);

        // 4. 提交审批并自动审批
        order.setOrderStatus(OrderStatusEnum.PENDING.getCode());
        inboundOrderMapper.updateById(order);
        approve(order.getInboundId(), null, null, "生产完工入库");

        log.info("生产完工入库完成: workOrderId={}, inboundId={}", workOrderId, order.getInboundId());
        return order.getInboundId();
    }

    @Override
    public List<InboundVO> getPendingApproval() {
        List<InventoryInboundOrder> orders = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>()
                        .eq(InventoryInboundOrder::getOrderStatus, OrderStatusEnum.PENDING.getCode())
                        .orderByAsc(InventoryInboundOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<InboundVO> getByDateRange(String startDate, String endDate) {
        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryInboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryInboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);

        List<InventoryInboundOrder> orders = inboundOrderMapper.selectList(wrapper);
        return convertToVOList(orders);
    }

    @Override
    public InboundVO getBySource(String sourceType, Long sourceId) {
        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryInboundOrder::getSourceType, sourceType)
                .eq(InventoryInboundOrder::getSourceId, sourceId);
        InventoryInboundOrder order = inboundOrderMapper.selectOne(wrapper);
        return convertToVO(order);
    }

    @Override
    public boolean updateStatus(Long inboundId, Integer status) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        order.setOrderStatus(status);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryInboundOrder> pageQuery(Map<String, Object> params) {
        String inboundNo = (String) params.get("inboundNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (inboundNo != null && !inboundNo.isEmpty()) {
            wrapper.like(InventoryInboundOrder::getInboundNo, inboundNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryInboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryInboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);

        Page<InventoryInboundOrder> page = new Page<>(pageNum, pageSize);
        return inboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        if (params != null && params.get("inboundId") != null) {
            Long inboundId = Long.valueOf(params.get("inboundId").toString());
            InboundVO detail = getDetail(inboundId);
            if (detail != null) {
                return Map.of("code", 200, "data", detail);
            }
        }
        return Map.of("code", 404, "message", "入库单不存在");
    }

    private static List<InboundVO> convertToVOList(List<InventoryInboundOrder> orders) {
        List<InboundVO> result = new ArrayList<>();
        for (InventoryInboundOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static InboundVO convertToVO(InventoryInboundOrder order) {
        if (order == null) {
            return null;
        }

        InboundVO vo = new InboundVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    private static List<InboundItemVO> convertToItemVOList(List<InventoryInboundItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<InboundItemVO> result = new ArrayList<>();
        for (InventoryInboundItem item : items) {
            result.add(convertToItemVO(item));
        }
        return result;
    }

    private static InboundItemVO convertToItemVO(InventoryInboundItem item) {
        if (item == null) {
            return null;
        }

        InboundItemVO vo = new InboundItemVO();
        vo.setInboundItemId(item.getItemId());
        vo.setInboundId(item.getInboundId());
        vo.setMaterialId(item.getMaterialId());
        vo.setMaterialCode(item.getMaterialCode());
        vo.setMaterialName(item.getMaterialName());
        vo.setSpecification(item.getSpecification());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        vo.setBatchNo(item.getBatchNo());
        vo.setProductionDate(item.getProductionDate());
        vo.setExpiryDate(item.getExpiryDate());
        vo.setLocationId(item.getLocationId());
        vo.setQualifiedQuantity(item.getQualifiedQuantity());
        vo.setRejectedQuantity(item.getRejectedQuantity());
        vo.setRejectReason(item.getRejectReason());
        vo.setSortOrder(item.getSortOrder());
        vo.setRemark(item.getRemark());

        return vo;
    }
}
