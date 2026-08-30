package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.domain.InventoryWarehouse;
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
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.inventory.service.ProductStockService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final InventoryMaterialMapper inventoryMaterialMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final EventPublisher eventPublisher;
    private final InventoryAlertService alertService;
    private final com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader;
    private final ProductStockService productStockService;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;

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
        if (params.get("supplierId") != null) order.setSupplierId(Long.valueOf(params.get("supplierId").toString()));
        order.setSupplierName((String) params.get("supplierName"));
        // 入库日期：缺省今天（DEV-436 修复：inbound_date NOT NULL 无默认值）
        if (params.get("inboundDate") != null && !params.get("inboundDate").toString().isEmpty()) {
            order.setInboundDate(LocalDate.parse(params.get("inboundDate").toString()));
        } else {
            order.setInboundDate(LocalDate.now());
        }
        order.setRemark((String) params.get("remark"));
        order.setOrderStatus(OrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.insert(order);

        // 保存明细（DEV-436 修复：原 create 不落 items，导致入库单无明细无法确认）
        Object itemsObj = params.get("items");
        if (itemsObj instanceof List<?> itemList && !itemList.isEmpty()) {
            List<InventoryInboundItem> items = new ArrayList<>();
            int sort = 1;
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmt = BigDecimal.ZERO;
            for (Object obj : itemList) {
                if (!(obj instanceof Map<?, ?> m)) continue;
                InventoryInboundItem item = new InventoryInboundItem();
                item.setInboundId(order.getInboundId());
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                item.setMaterialCode((String) m.get("materialCode"));
                item.setMaterialName((String) m.get("materialName"));
                item.setSpecification((String) m.get("specification"));
                item.setUnit((String) m.get("unit"));
                if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
                if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                item.setAmount(qty.multiply(price));
                item.setBatchNo((String) m.get("batchNo"));
                if (m.get("productionDate") != null && !m.get("productionDate").toString().isEmpty()) {
                    item.setProductionDate(LocalDate.parse(m.get("productionDate").toString()));
                }
                if (m.get("expiryDate") != null && !m.get("expiryDate").toString().isEmpty()) {
                    item.setExpiryDate(LocalDate.parse(m.get("expiryDate").toString()));
                }
                if (m.get("locationId") != null) item.setLocationId(Long.valueOf(m.get("locationId").toString()));
                item.setRemark((String) m.get("remark"));
                item.setSortOrder(sort++);
                items.add(item);
                totalQty = totalQty.add(qty);
                totalAmt = totalAmt.add(item.getAmount());
            }
            if (!items.isEmpty()) {
                inboundItemMapper.batchInsert(items);
                order.setTotalQuantity(totalQty);
                order.setTotalAmount(totalAmt);
                inboundOrderMapper.updateById(order);
            }
        }
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.confirmed", bizId = "#inboundId", bizType = "'inventory'")
    public boolean confirm(Long inboundId, Long operatorId, String operatorName) {
        // DEV-651 方案A：行锁查询，锁住单据行直到事务提交，并发下第二个请求阻塞后状态校验失败，杜绝重复入库
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        // 2026-08-11 业务定稿：审核不能跳过——只有“已批准”才能确认入库，待审批必须先走审批
        if (!OrderStatusEnum.APPROVED.getValue().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法确认（需先审批通过）: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        // 库存操作统一发生在 confirm：加库存+流水+置完成
        addStock(order, operatorId, operatorName, "确认入库");
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getValue());
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
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (OrderStatusEnum.COMPLETED.getValue().equals(order.getOrderStatus())) {
            log.error("已完成的入库单无法取消: inboundId={}", inboundId);
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.CANCELLED.getValue());
        order.setRemark(reason);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.submitted", bizId = "#inboundId", bizType = "'inventory'")
    public boolean submitApprove(Long inboundId) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        // DEV-651：只有草稿/已驳回/已取消状态的单才能提交审批
        Integer status = order.getOrderStatus();
        if (!OrderStatusEnum.DRAFT.getValue().equals(status)
                && !OrderStatusEnum.REJECTED.getValue().equals(status)
                && !OrderStatusEnum.CANCELLED.getValue().equals(status)) {
            log.error("入库单状态不允许提交审批: inboundId={}, status={}", inboundId, status);
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.PENDING.getValue());
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.approved", bizId = "#inboundId", bizType = "'inventory'")
    public boolean approve(Long inboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法审批: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        // 2026-08-11 业务定稿：审批通过 = 确认入库（一步到位）——直接加库存+流水+置完成，不再需要单独的"确认入库"环节
        addStock(order, approverId, approverName, "审批通过入库");
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getValue());
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
    @Event(value = "inventory.inbound.rejected", bizId = "#inboundId", bizType = "'inventory'")
    public boolean reject(Long inboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法驳回: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.REJECTED.getValue());
        order.setRemark(remark);
        return inboundOrderMapper.updateById(order) > 0;
    }

    /**
     * 执行入库加库存（DEV-651：库存操作统一由 confirm 调用）
     * 原 approve 中的加库存逻辑抽取，供 confirm 直接使用
     */
    private void addStock(InventoryInboundOrder order, Long operatorId, String operatorName, String remark) {
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(order.getInboundId());
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

            // 记录流水（DEV-651 补：before/after 为 NOT NULL，入库=加库存，before=当前汇总-本次数量）
            java.math.BigDecimal currentTotal = java.math.BigDecimal.ZERO;
            InventoryStock cur = stockMapper.selectByMaterialId(item.getMaterialId());
            if (cur != null && cur.getTotalQuantity() != null) {
                currentTotal = cur.getTotalQuantity();
            }
            InventoryTransaction tx = new InventoryTransaction();
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getWarehouseId());
            tx.setLocationId(item.getLocationId());
            tx.setTransactionType("INBOUND");
            tx.setSourceType(order.getSourceType() != null ? order.getSourceType() : "PURCHASE");
            tx.setSourceId(order.getInboundId());
            tx.setSourceNo(order.getInboundNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(item.getQuantity());
            tx.setBeforeQuantity(currentTotal.subtract(item.getQuantity()));
            tx.setAfterQuantity(currentTotal);
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getAmount());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            tx.setRemark(remark != null ? remark : "入库确认");
            transactionMapper.insert(tx);
        }
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
        order.setTraceId(po.getTraceId()); // 链路追踪（DEV-568）：采购到货→入库单继承
        order.setWarehouseId(1L); // 默认仓库
        order.setInboundDate(LocalDate.now());
        order.setOrderStatus(OrderStatusEnum.DRAFT.getValue());
        // 供应商/创建人从采购单带过来，避免列表页数据空白
        order.setSupplierId(po.getSupplierId());
        order.setSupplierName(po.getSupplierName());
        try {
            order.setCreateBy(com.jjx.system.utils.SecurityUtils.getUsername());
        } catch (Exception ignore) { }
        inboundOrderMapper.insert(order);

        // 5. 创建入库单明细
        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
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
            inboundItem.setBatchNo(order.getInboundNo() + "-" + sort); // 批次号=入库单号-行序号（2026-08-11 修复：原 PO-单号-行序号 在多凭证时重复，凭证↔批次断链）
            inboundItem.setSortOrder(sort++);
            inboundItemMapper.insert(inboundItem);
            totalQty = totalQty.add(receiveQty);
            if (item.getAmount() != null) {
                totalAmt = totalAmt.add(item.getAmount());
            }

            // 更新采购订单已收数量
            purchaseOrderItemMapper.updateReceivedQuantity(item.getItemId(), receiveQty);
        }

        // 主表汇总字段补全
        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmt);
        inboundOrderMapper.updateById(order);

        // 6. 提交审批并自动审批
        order.setOrderStatus(OrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);
        approve(order.getInboundId(), null, null, "采购到货入库");

        try { eventPublisher.fire("purchase.arrived", Map.of("sourceNo", order.getSourceNo(), "inboundId", String.valueOf(order.getInboundId()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("采购入库完成: purchaseOrderId={}, inboundId={}", purchaseOrderId, order.getInboundId());
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInboundRecordFromPurchase(Long purchaseOrderId) {
        log.info("采购收货自动生成入库单: purchaseOrderId={}", purchaseOrderId);
        PurchaseOrder po = purchaseOrderMapper.selectById(purchaseOrderId);
        if (po == null) {
            log.warn("采购订单不存在，跳过自动入库: {}", purchaseOrderId);
            return null;
        }
        // 2026-08-18：每次收货生成独立入库单（批次），不再删除重建——
        // 采购单详情「入库凭证」按时间线区分每次收货（第1次收500、第2次收500各自一张单）
        String baseInboundNo = "PO-" + po.getOrderNo();
        List<InventoryInboundOrder> existingList = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>().likeRight(InventoryInboundOrder::getInboundNo, baseInboundNo));
        // 已生成入库单明细量（含待确认——待确认单也占用了收货量，防下一张重复入；驳回/删除后自动重新计入）
        Map<Long, BigDecimal> alreadyInByMaterial = new HashMap<>();
        for (InventoryInboundOrder done : existingList) {
            List<InventoryInboundItem> doneItems = inboundItemMapper.selectByInboundId(done.getInboundId());
            for (InventoryInboundItem di : doneItems) {
                if (di.getMaterialId() == null || di.getQuantity() == null) continue;
                alreadyInByMaterial.merge(di.getMaterialId(), di.getQuantity(), BigDecimal::add);
            }
        }

        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByOrderId(purchaseOrderId);
        if (items.isEmpty()) {
            log.warn("采购订单无明细，跳过自动入库: {}", purchaseOrderId);
            return null;
        }

        // 计算各明细未入库数量（本次已收 - 已完成已入）
        List<PurchaseOrderItem> toInItems = new ArrayList<>();
        List<BigDecimal> toInQtys = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            if (item.getReceivedQuantity() == null || item.getReceivedQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal alreadyIn = alreadyInByMaterial.getOrDefault(item.getMaterialId(), BigDecimal.ZERO);
            BigDecimal toIn = item.getReceivedQuantity().subtract(alreadyIn);
            if (toIn.compareTo(BigDecimal.ZERO) <= 0) continue;
            toInItems.add(item);
            toInQtys.add(toIn);
        }
        if (toInItems.isEmpty()) {
            log.info("采购订单{} 无待入库数量，跳过: {}", purchaseOrderId, baseInboundNo);
            return null;
        }

        // 每次收货新建一张入库单（序号递增 PO-xxx、PO-xxx-2、PO-xxx-3…）；状态=待审批，仓库确认后才加库存（2026-08-11 业务定稿：收货≠入库）
        final InventoryInboundOrder order;
        String inboundNo = existingList.isEmpty() ? baseInboundNo : baseInboundNo + "-" + (existingList.size() + 1);
        order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("PURCHASE");
        order.setSourceType("PURCHASE");
        order.setSourceId(purchaseOrderId);
        order.setSourceNo(po.getOrderNo());
        order.setTraceId(po.getTraceId());
        order.setWarehouseId(1L);
        order.setInboundDate(LocalDate.now());
        order.setOrderStatus(OrderStatusEnum.PENDING.getValue()); // 待审批：收货后需仓库审批→确认入库才加库存（2026-08-11 业务定稿：收货≠入库）
        order.setRemark("采购收货自动入库（DEV-624）批次" + existingList.size());
        // 供应商/创建人从采购单带过来，避免列表页数据空白
        order.setSupplierId(po.getSupplierId());
        order.setSupplierName(po.getSupplierName());
        try {
            order.setCreateBy(com.jjx.system.utils.SecurityUtils.getUsername());
        } catch (Exception ignore) { }
        inboundOrderMapper.insert(order);
        log.info("采购订单{} 新建收货入库单 {}", purchaseOrderId, inboundNo);

        // 重建明细=未入库数量
        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (int i = 0; i < toInItems.size(); i++) {
            PurchaseOrderItem item = toInItems.get(i);
            BigDecimal toIn = toInQtys.get(i);
            InventoryInboundItem inboundItem = new InventoryInboundItem();
            inboundItem.setInboundId(order.getInboundId());
            inboundItem.setMaterialId(item.getMaterialId());
            inboundItem.setMaterialCode(item.getMaterialCode());
            inboundItem.setMaterialName(item.getMaterialName());
            inboundItem.setQuantity(toIn);
            inboundItem.setUnitPrice(item.getUnitPrice());
            BigDecimal itemAmt = (item.getAmount() == null || item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                    ? null
                    : item.getAmount().multiply(toIn.divide(item.getQuantity(), 4, java.math.RoundingMode.HALF_UP));
            inboundItem.setAmount(itemAmt);
            inboundItem.setBatchNo(order.getInboundNo() + "-" + sort); // 批次号=入库单号-行序号（2026-08-11 修复：原 PO-单号-行序号 在多凭证时重复，凭证↔批次断链）
            inboundItem.setSortOrder(sort++);
            inboundItemMapper.insert(inboundItem);
            totalQty = totalQty.add(toIn);
            if (itemAmt != null) {
                totalAmt = totalAmt.add(itemAmt);
            }
        }
        // 主表汇总字段补全（列表页/详情页展示用）
        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmt);
        inboundOrderMapper.updateById(order);
        try { eventPublisher.fire("purchase.arrived", Map.of("sourceNo", order.getSourceNo(), "inboundId", String.valueOf(order.getInboundId()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("采购收货自动入库单生成/更新: purchaseOrderId={}, inboundId={}, qty={}", purchaseOrderId, order.getInboundId(), totalQty);
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

        // DEV-936（2026-08-12）：工单未完工禁止生成完工入库单（与 DEV-053 完工质检门一致），
        // 否则 finishedQuantity=0 导致入库数量记 0、库存不入账
        if (!com.jjx.production.enums.OrderStatusEnum.COMPLETED.getValue().equals(prodOrder.getOrderStatus())) {
            String statusName = "状态码" + prodOrder.getOrderStatus();
            try {
                var pe = com.jjx.production.enums.OrderStatusEnum.getByValue(prodOrder.getOrderStatus());
                statusName = pe.getLabel();
            } catch (Exception ignored) {}
            throw new BusinessException("工单未完工，不能生成完工入库单（当前状态：" + statusName + "）");
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
        order.setTraceId(prodOrder.getTraceId()); // 链路追踪（DEV-568）：工单→完工入库单继承
        order.setInboundDate(LocalDate.now());
        // DEV-679：工单无仓库字段，默认取第一个启用仓库（与出库侧 createFromProduction 一致）
        try {
            InventoryWarehouse defaultWh = warehouseMapper.selectOne(
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
        order.setOrderStatus(OrderStatusEnum.DRAFT.getValue());
        inboundOrderMapper.insert(order);

        // 3. 创建入库明细（DEV-579：物料=成品物料档案 F类型，产品ID→物料ID映射）
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setInboundId(order.getInboundId());
        // 通过产品ID查成品物料档案（material_type=F），无档案则回退用产品ID（兼容旧数据）
        Long materialId = prodOrder.getProductId();
        String materialCode = prodOrder.getProductCode();
        String materialName = prodOrder.getProductName();
        if (prodOrder.getProductId() != null) {
            com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                            .eq(com.jjx.inventory.domain.InventoryMaterial::getProductId, prodOrder.getProductId())
                            .eq(com.jjx.inventory.domain.InventoryMaterial::getMaterialType, "F")
                            .last("LIMIT 1"));
            if (mat != null) {
                materialId = mat.getMaterialId();
                materialCode = mat.getMaterialCode();
                materialName = mat.getMaterialName();
            }
        }
        inboundItem.setMaterialId(materialId);
        inboundItem.setMaterialCode(materialCode);
        inboundItem.setMaterialName(materialName);
        // 068定稿：入库产品数量=最后一道工序/完工检验合格数（052口径 finishedQuantity，非工序汇总 completedQuantity）
        BigDecimal inboundQty = (prodOrder.getFinishedQuantity() != null && prodOrder.getFinishedQuantity().compareTo(BigDecimal.ZERO) > 0)
                ? prodOrder.getFinishedQuantity()
                : (prodOrder.getCompletedQuantity() != null ? prodOrder.getCompletedQuantity() : prodOrder.getPlannedQuantity());
        inboundItem.setQuantity(inboundQty);
        inboundItem.setBatchNo("BATCH-" + prodOrder.getOrderNo());
        inboundItem.setSortOrder(1);
        inboundItemMapper.insert(inboundItem);

        // 4. 提交审批并自动审批
        order.setOrderStatus(OrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);
        approve(order.getInboundId(), null, null, "生产完工入库");

        // DEV-20260810-096：完工入库=产品入库（产品维度独立记账，入 product_stock 表）
        // 概念红线：完工入库入的是产品库存，不是物料不是材料；产品库存与物料库存各自独立记账
        try {
            BigDecimal productQty = inboundItem.getQuantity();
            if (prodOrder.getProductId() != null && productQty != null) {
                productStockService.increase(prodOrder.getProductId(), prodOrder.getProductCode(),
                        prodOrder.getProductName(), productQty);
                log.info("完工入库同步产品库存+: productId={}, qty={}", prodOrder.getProductId(), productQty);
            }
        } catch (Exception e) {
            log.warn("完工入库同步产品库存失败（不影响入库主流程）: {}", e.getMessage());
        }

        // 057定稿：产品入库确认成功后回写订单 produced_quantity += 入库量（账实最准，不是完工就写）
        try {
            if (prodOrder.getSalesOrderId() != null) {
                com.jjx.sales.domain.entity.SalesOrder salesOrder = salesOrderMapper.selectById(prodOrder.getSalesOrderId());
                if (salesOrder != null && inboundItem.getQuantity() != null) {
                    int produced = salesOrder.getProducedQuantity() != null ? salesOrder.getProducedQuantity() : 0;
                    salesOrder.setProducedQuantity(produced + inboundItem.getQuantity().intValue());
                    salesOrderMapper.updateById(salesOrder);
                    log.info("完工入库回写订单 produced_quantity: orderId={}, 本次+{}，累计={}",
                            prodOrder.getSalesOrderId(), inboundItem.getQuantity().intValue(), salesOrder.getProducedQuantity());
                }
            }
        } catch (Exception e) {
            log.warn("完工入库回写订单 produced_quantity 失败（不影响入库主流程）: {}", e.getMessage());
        }

        log.info("生产完工入库完成: workOrderId={}, inboundId={}", workOrderId, order.getInboundId());
        return order.getInboundId();
    }

    @Override
    public List<InboundVO> getPendingApproval() {
        List<InventoryInboundOrder> orders = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>()
                        .eq(InventoryInboundOrder::getOrderStatus, OrderStatusEnum.PENDING.getValue())
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
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long inboundId, Integer status) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
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

    private List<InboundVO> convertToVOList(List<InventoryInboundOrder> orders) {
        List<InboundVO> result = new ArrayList<>();
        for (InventoryInboundOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private InboundVO convertToVO(InventoryInboundOrder order) {
        if (order == null) {
            return null;
        }

        InboundVO vo = new InboundVO();
        BeanUtils.copyProperties(order, vo);
        // 状态码/名称（前端展示用，与实体 Integer 字段对齐）
        vo.setStatus(order.getOrderStatus());
        vo.setStatusName(com.jjx.inventory.enums.OrderStatusEnum.getByValue(order.getOrderStatus()) != null
                ? com.jjx.inventory.enums.OrderStatusEnum.getByValue(order.getOrderStatus()).getLabel() : null);
        // 入库类型名称
        vo.setInboundTypeName(inboundTypeName(order.getInboundType()));
        // 仓库名称
        if (order.getWarehouseId() != null) {
            try {
                InventoryWarehouse wh = warehouseMapper.selectById(order.getWarehouseId());
                if (wh != null) {
                    vo.setWarehouseName(wh.getWarehouseName());
                }
            } catch (Exception ignore) { }
        }
        // 审核状态名称（approve_status 为未维护的死字段，2026-08-11 起不再使用，统一以 order_status 为准）
        return vo;
    }

    /** 入库类型显示名 */
    private static String inboundTypeName(String inboundType) {
        if (inboundType == null) return null;
        return switch (inboundType) {
            case "PURCHASE" -> "采购入库";
            case "PRODUCTION_FINISH" -> "生产入库";
            case "RETURN" -> "退货入库";
            case "TRANSFER" -> "调拨入库";
            case "OTHER" -> "其他入库";
            default -> inboundType;
        };
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

    @Override
    public byte[] exportPdf(Long inboundId) {
        InboundVO vo = getDetail(inboundId);
        if (vo == null) {
            throw new BusinessException("入库单不存在: " + inboundId);
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("入库单号", vo.getInboundNo());
        info.put("入库日期", vo.getInboundDate() == null ? "" : vo.getInboundDate().toString());
        info.put("入库类型", vo.getInboundType() == null ? "-" : vo.getInboundType());
        info.put("来源单号", vo.getSourceNo() == null ? "-" : vo.getSourceNo());
        info.put("仓库", vo.getWarehouseName() == null ? "-" : vo.getWarehouseName());
        info.put("库位", vo.getLocationName() == null ? "-" : vo.getLocationName());
        info.put("供应商", vo.getSupplierName() == null ? "-" : vo.getSupplierName());
        info.put("验收人", vo.getInspectorName() == null ? "-" : vo.getInspectorName());
        info.put("验收结果", vo.getInspectionResult() == null ? "-" : vo.getInspectionResult());

        java.util.List<String[]> rows = new java.util.ArrayList<>();
        if (vo.getItems() != null) {
            for (com.jjx.inventory.dto.vo.InboundItemVO item : vo.getItems()) {
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
                .withConfig(pdfConfigLoader.load())
                .withConfig(pdfConfigLoader.load())
                .title("入  库  单")
                .info(info)
                .items(new String[]{"序号", "物料编码", "物料名称/规格", "数量", "单位", "单价", "金额", "批次"}, rows)
                .amounts(new String[][]{
                        {"总数量", vo.getTotalQuantity() == null ? "" : df.format(vo.getTotalQuantity())},
                        {"总金额", vo.getTotalAmount() == null ? "" : df.format(vo.getTotalAmount())},
                })
                .remark(vo.getRemark())
                .signatures("验收人：" + (vo.getInspectorName() == null ? "" : vo.getInspectorName()),
                        "仓管确认：", "日期：")
                .toBytes();
    }
}
