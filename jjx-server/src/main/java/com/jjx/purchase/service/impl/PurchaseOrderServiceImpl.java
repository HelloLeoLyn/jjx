package com.jjx.purchase.service.impl;

import com.jjx.purchase.domain.enums.InquiryStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.annotation.ExcelColumn;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.converter.PurchaseConverter;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.enums.ApprovalStatusEnum;
import com.jjx.purchase.domain.enums.PaymentStatusEnum;
import com.jjx.purchase.domain.enums.PurchaseExceptionEnum;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import com.jjx.purchase.domain.vo.*;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.service.IPurchaseOrderService;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 采购订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements IPurchaseOrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PurchaseConverter purchaseConverter;
    private final com.jjx.inventory.mapper.InventoryStockItemMapper stockItemMapper;
    private final com.jjx.inventory.mapper.InventoryStockMapper stockMapper;
    private final com.jjx.inventory.mapper.InventoryTransactionMapper transactionMapper;

    @Override
    public PageResult<PurchaseOrderVO> page(PurchaseOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = buildQueryWrapper(queryDTO);
        // 排序
        wrapper.orderByDesc(PurchaseOrder::getCreateTime);
        Page<PurchaseOrder> page = new Page<>(queryDTO.getPageNum(),queryDTO.getPageSize());
        orderMapper.selectPage(page,wrapper);
        List<PurchaseOrderVO> voList = purchaseConverter.toVOList(page.getRecords());

        return PageResult.build(voList,page.getTotal());
    }

    @Override
    public PurchaseOrderVO selectOrderById(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        PurchaseOrderVO orderVO = purchaseConverter.toVO(order);

        // 查询订单明细
        List<PurchaseOrderItemVO> itemVOs = selectOrderItemsById(orderId);
        orderVO.setItems(itemVOs);

        return orderVO;
    }

    @Override
    public List<PurchaseOrderItemVO> selectOrderItemList(Long orderId) {
        LambdaQueryWrapper<PurchaseOrderItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseOrderItem::getOrderId, orderId);
        wrapper.orderByAsc(PurchaseOrderItem::getItemOrder);
        List<PurchaseOrderItem> items = orderItemMapper.selectList(wrapper);
        return purchaseConverter.toItemVOList(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertOrder(PurchaseOrderDTO orderDTO) {
        // 检查订单号是否唯一
        if (checkOrderNoUnique(orderDTO.getOrderNo())) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NO_DUPLICATE.getMessage());
        }

        // 验证供应商信息
        if (orderDTO.getSupplierId() == null || StringUtils.isEmpty(orderDTO.getSupplierName())) {
            throw new BusinessException(PurchaseExceptionEnum.SUPPLIER_INFO_INCOMPLETE.getMessage());
        }

        // 验证订单明细
        if (orderDTO.getItems() == null || orderDTO.getItems().isEmpty()) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_ITEMS_EMPTY.getMessage());
        }

        // 计算订单金额
        calculateOrderAmount(orderDTO);

        // 转换实体
        PurchaseOrder order = purchaseConverter.toEntity(orderDTO);

        // 设置默认状态
        if (order.getApprovalStatus() == null) {
            order.setApprovalStatus(ApprovalStatusEnum.DRAFT.getCode());
        }
        if (order.getReceiptStatus() == null) {
            order.setReceiptStatus(ReceiptStatusEnum.PENDING.getCode());
        }
        if (order.getPaymentStatus() == null) {
            order.setPaymentStatus(PaymentStatusEnum.PENDING.getCode());
        }
        if (order.getPaidAmount() == null) {
            order.setPaidAmount(BigDecimal.ZERO);
        }
        if (order.getUrgentFlag() == null) {
            order.setUrgentFlag(false);
        }

        // 保存订单
        int result = orderMapper.insert(order);
        if (result <= 0) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_SAVE_FAILED.getMessage());
        }

        // 保存订单明细
        saveOrderItems(order.getOrderId(), orderDTO.getItems());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOrder(PurchaseOrderDTO orderDTO) {
        if (orderDTO.getOrderId() == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_ID_REQUIRED.getMessage());
        }

        // 检查订单是否存在
        PurchaseOrder existingOrder = orderMapper.selectById(orderDTO.getOrderId());
        if (existingOrder == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 检查订单状态是否允许修改（只有草稿和已拒绝可修改）
        if (!Objects.equals(ApprovalStatusEnum.DRAFT.getCode(), existingOrder.getApprovalStatus())
                && !Objects.equals(ApprovalStatusEnum.REJECTED.getCode(), existingOrder.getApprovalStatus())) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_EDITABLE.getMessage());
        }

        // 验证供应商信息
        if (orderDTO.getSupplierId() == null || StringUtils.isEmpty(orderDTO.getSupplierName())) {
            throw new BusinessException(PurchaseExceptionEnum.SUPPLIER_INFO_INCOMPLETE.getMessage());
        }

        // 验证订单明细
        if (orderDTO.getItems() == null || orderDTO.getItems().isEmpty()) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_ITEMS_EMPTY.getMessage());
        }

        // 计算订单金额
        calculateOrderAmount(orderDTO);

        // 转换实体
        PurchaseOrder order = purchaseConverter.toEntity(orderDTO);

        // 更新订单
        int result = orderMapper.updateById(order);
        if (result <= 0) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_UPDATE_FAILED.getMessage());
        }

        // 删除原有明细
        LambdaQueryWrapper<PurchaseOrderItem> deleteWrapper = Wrappers.lambdaQuery();
        deleteWrapper.eq(PurchaseOrderItem::getOrderId, order.getOrderId());
        orderItemMapper.delete(deleteWrapper);

        // 保存新的订单明细
        saveOrderItems(order.getOrderId(), orderDTO.getItems());

        return result;
    }



    @Override
    public boolean checkOrderNoUnique(String orderNo) {
        return orderMapper.checkOrderNoUnique(orderNo) > 0;
    }

    @Override
    public int updateOrderStatus(Long orderId, Integer approvalStatus) {
        return orderMapper.updateApprovalStatus(orderId, approvalStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "purchase.submitted", bizId = "#orderId", bizType = "'purchase'")
    public int submitOrder(Long orderId) {
        // 检查订单是否存在
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 检查订单状态（只有草稿和已拒绝可提交）
        if (!Objects.equals(ApprovalStatusEnum.DRAFT.getCode(), order.getApprovalStatus())
                && !Objects.equals(ApprovalStatusEnum.REJECTED.getCode(), order.getApprovalStatus())) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_SUBMITTABLE.getMessage());
        }

        // 检查订单明细
        LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = Wrappers.lambdaQuery();
        itemWrapper.eq(PurchaseOrderItem::getOrderId, orderId);
        long itemCount = orderItemMapper.selectCount(itemWrapper);
        if (itemCount == 0) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_ITEMS_EMPTY.getMessage());
        }

        // 更新订单状态为待审批
        order.setApprovalStatus(ApprovalStatusEnum.PENDING.getCode());
        order.setUpdateTime(LocalDateTime.now());

        return orderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSubmitOrders(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessException(PurchaseExceptionEnum.BATCH_NO_ORDERS_SELECTED.getMessage());
        }

        int successCount = 0;
        List<String> failedOrders = new ArrayList<>();

        for (Long orderId : orderIds) {
            try {
                submitOrder(orderId);
                successCount++;
            } catch (BusinessException e) {
                failedOrders.add("订单ID[" + orderId + "]: " + e.getMessage());
                log.warn("批量提交订单失败: orderId={}, reason={}", orderId, e.getMessage());
            }
        }

        if (successCount == 0) {
            throw new BusinessException(PurchaseExceptionEnum.BATCH_SUBMIT_FAILED.getMessage() + ": " + String.join("; ", failedOrders));
        }

        if (!failedOrders.isEmpty()) {
            log.warn("批量提交部分失败: 成功{}个, 失败{}个 - {}", successCount, failedOrders.size(), String.join("; ", failedOrders));
        }

        return successCount;
    }

    @Override
    @Event(value = "purchase.approved", bizId = "#dto.orderId", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int approveOrder(PurchaseOrderApprovalDTO dto) {
        // 检查订单是否存在
        PurchaseOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 检查订单状态
        if (!Objects.equals(ApprovalStatusEnum.PENDING.getCode(), order.getApprovalStatus())) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_APPROVABLE.getMessage());
        }
        Integer targetStatus = Objects.equals(ApprovalStatusEnum.APPROVED.getCode(), dto.getApprovalStatus()) ?ApprovalStatusEnum.APPROVED.getCode():
                ApprovalStatusEnum.REJECTED.getCode();
        // 更新审批信息
        LambdaUpdateWrapper<PurchaseOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(PurchaseOrder::getApprovalStatus, targetStatus)
                .set(PurchaseOrder::getApprovalComment,dto.getApprovalComment())
                .set(PurchaseOrder::getApprovalTime,LocalDateTime.now())
                .set(PurchaseOrder::getApproverName,SecurityUtils.getUsername())
                .set(PurchaseOrder::getApproverId,SecurityUtils.getUserId())
                .eq(PurchaseOrder::getOrderId,dto.getOrderId())
                .eq(PurchaseOrder::getApprovalStatus,order.getApprovalStatus());
        return orderMapper.update(updateWrapper);
    }

    @Override
    public int updateReceiptStatus(Long orderId, Integer receiptStatus) {
        return orderMapper.updateReceiptStatus(orderId, receiptStatus);
    }

    @Override
    @Event(value = "purchase.item_received", bizId = "#orderId", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int receiveOrderItem(Long orderId, Long itemId, BigDecimal receivedQuantity, String inspectionResult, String inspectionRemark) {
        // 检查订单是否存在
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 检查订单状态（已批准或待审批可收货）
        Integer status = order.getApprovalStatus();
        if (!Objects.equals(ApprovalStatusEnum.PENDING.getCode(), status)
                && !Objects.equals(ApprovalStatusEnum.APPROVED.getCode(), status)) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_RECEIVABLE.getMessage());
        }

        // 检查明细项
        PurchaseOrderItem item = orderItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_ITEM_NOT_FOUND.getMessage());
        }

        // 更新收货数量
        BigDecimal newReceivedQuantity = item.getReceivedQuantity() != null ?
                item.getReceivedQuantity().add(receivedQuantity) : receivedQuantity;
        item.setReceivedQuantity(newReceivedQuantity);

        // 更新检验结果
        if (StringUtils.isNotEmpty(inspectionResult)) {
            item.setInspectionResult(inspectionResult);
        }
        if (StringUtils.isNotEmpty(inspectionRemark)) {
            item.setInspectionRemark(inspectionRemark);
        }

        // 更新收货状态
        if (newReceivedQuantity.compareTo(item.getQuantity()) >= 0) {
            item.setReceiptStatus(ReceiptStatusEnum.COMPLETED.getCode());
        } else if (newReceivedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            item.setReceiptStatus(ReceiptStatusEnum.PARTIALLY_RECEIVED.getCode());
        }

        // 更新明细项
        int result = orderItemMapper.updateById(item);

        // 检查订单整体收货状态
        updateOrderReceiptStatus(orderId);

        return result;
    }

    @Override
    @Event(value = "purchase.payment_updated", bizId = "#orderId", bizType = "'purchase'")
    public int updatePaymentInfo(Long orderId, BigDecimal paidAmount, Integer paymentStatus) {
        return orderMapper.updatePaymentInfo(orderId, paidAmount, paymentStatus);
    }

    @Override
    public int updateActualDeliveryDate(Long orderId, LocalDate actualDeliveryDate) {
        return orderMapper.updateActualDeliveryDate(orderId, actualDeliveryDate);
    }

    @Override
    public List<PurchaseOrderVO> selectOrdersBySupplierId(Long supplierId) {
        List<PurchaseOrder> orders = orderMapper.selectOrdersBySupplierId(supplierId);
        return purchaseConverter.toVOList(orders);
    }

    @Override
    public List<PurchaseOrderVO> selectOrdersByStatus(Integer approvalStatus) {
        List<PurchaseOrder> orders = orderMapper.selectOrdersByStatus(approvalStatus);
        return purchaseConverter.toVOList(orders);
    }



    @Override
    public List<PurchaseOrderVO> selectPendingReceiptOrders() {
        List<PurchaseOrder> orders = orderMapper.selectPendingReceiptOrders();
        return purchaseConverter.toVOList(orders);
    }

    @Override
    public List<PurchaseOrderVO> selectPendingPaymentOrders() {
        List<PurchaseOrder> orders = orderMapper.selectPendingPaymentOrders();
        return purchaseConverter.toVOList(orders);
    }


    @Override
    public List<PurchaseOrderVO> selectOrdersByDateRange(LocalDate startDate, LocalDate endDate) {
        List<PurchaseOrder> orders = orderMapper.selectOrdersByDateRange(startDate, endDate);
        return purchaseConverter.toVOList(orders);
    }

    @Override
    public String exportOrderList(PurchaseOrderQueryDTO queryDTO) {
        // 查询订单列表
        List<PurchaseOrder> orders = orderMapper.selectList(buildQueryWrapper(queryDTO));
        if (orders.isEmpty()) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_EXPORT_NO_DATA.getMessage());
        }

        // 转换为导出VO
        List<PurchaseOrderExportVO> exportList = purchaseConverter.toExportVOList(orders);

        // 生成导出文件路径
        String fileName = "采购订单列表_" + LocalDate.now().toString();
        String filePath = generateExportFilePath(fileName);

        // 使用POI生成Excel文件
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("采购订单列表");
            writeExportHeader(sheet, PurchaseOrderExportVO.class);
            writeExportData(sheet, exportList, PurchaseOrderExportVO.class);
            autoSizeColumns(sheet, PurchaseOrderExportVO.class);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            log.error("导出采购订单列表失败", e);
            throw new BusinessException(PurchaseExceptionEnum.ORDER_EXPORT_FAILED.getMessage() + ": " + e.getMessage());
        }

        log.info("导出采购订单列表成功，文件路径: {}", filePath);
        return filePath;
    }

    @Override
    public String exportOrderDetail(Long orderId) {
        // 查询订单
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 查询订单明细
        LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = Wrappers.lambdaQuery();
        itemWrapper.eq(PurchaseOrderItem::getOrderId, orderId);
        itemWrapper.orderByAsc(PurchaseOrderItem::getItemOrder);
        List<PurchaseOrderItem> items = orderItemMapper.selectList(itemWrapper);

        // 转换为导出VO
        List<PurchaseOrderItemExportVO> exportList = purchaseConverter.toItemExportVOList(items);

        // 设置订单号
        for (PurchaseOrderItemExportVO vo : exportList) {
            vo.setOrderNo(order.getOrderNo());
        }

        // 生成导出文件路径
        String fileName = "采购订单明细_" + order.getOrderNo();
        String filePath = generateExportFilePath(fileName);

        // 使用POI生成Excel文件
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("采购订单明细");
            writeExportHeader(sheet, PurchaseOrderItemExportVO.class);
            writeExportData(sheet, exportList, PurchaseOrderItemExportVO.class);
            autoSizeColumns(sheet, PurchaseOrderItemExportVO.class);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            log.error("导出采购订单明细失败", e);
            throw new BusinessException(PurchaseExceptionEnum.ORDER_EXPORT_FAILED.getMessage() + ": " + e.getMessage());
        }

        log.info("导出采购订单明细成功，文件路径: {}", filePath);
        return filePath;
    }

    @Override
    public Object getOrderStatistics() {
        // 查询所有订单
        List<PurchaseOrder> allOrders = orderMapper.selectList(Wrappers.emptyWrapper());

        // 统计总数
        long totalCount = allOrders.size();

        // 按审批状态统计
        long draftCount = allOrders.stream()
                .filter(o -> Objects.equals(ApprovalStatusEnum.DRAFT.getCode(), o.getApprovalStatus()))
                .count();
        long pendingCount = allOrders.stream()
                .filter(o -> Objects.equals(ApprovalStatusEnum.PENDING.getCode(), o.getApprovalStatus()))
                .count();
        long approvedCount = allOrders.stream()
                .filter(o -> Objects.equals(ApprovalStatusEnum.APPROVED.getCode(), o.getApprovalStatus()))
                .count();
        long rejectedCount = allOrders.stream()
                .filter(o -> Objects.equals(ApprovalStatusEnum.REJECTED.getCode(), o.getApprovalStatus()))
                .count();
        long cancelledCount = allOrders.stream()
                .filter(o -> Objects.equals(ApprovalStatusEnum.CANCELLED.getCode(), o.getApprovalStatus()))
                .count();

        // 按收货状态统计
        long pendingReceiptCount = allOrders.stream()
                .filter(o -> Objects.equals(ReceiptStatusEnum.PENDING.getCode(), o.getReceiptStatus()))
                .count();
        long partiallyReceivedCount = allOrders.stream()
                .filter(o -> Objects.equals(ReceiptStatusEnum.PARTIALLY_RECEIVED.getCode(), o.getReceiptStatus()))
                .count();
        long completedReceiptCount = allOrders.stream()
                .filter(o -> Objects.equals(ReceiptStatusEnum.COMPLETED.getCode(), o.getReceiptStatus()))
                .count();

        // 按付款状态统计
        long pendingPaymentCount = allOrders.stream()
                .filter(o -> Objects.equals(PaymentStatusEnum.PENDING.getCode(), o.getPaymentStatus()))
                .count();
        long partiallyPaidCount = allOrders.stream()
                .filter(o -> Objects.equals(PaymentStatusEnum.PARTIALLY_PAID.getCode(), o.getPaymentStatus()))
                .count();
        long completedPaymentCount = allOrders.stream()
                .filter(o -> Objects.equals(PaymentStatusEnum.COMPLETED.getCode(), o.getPaymentStatus()))
                .count();

        // 紧急订单统计
        long urgentCount = allOrders.stream()
                .filter(o -> o.getUrgentFlag() != null && o.getUrgentFlag())
                .count();

        // 金额统计
        BigDecimal totalAmount = allOrders.stream()
                .filter(o -> o.getOrderTotalAmount() != null)
                .map(PurchaseOrder::getOrderTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = allOrders.stream()
                .filter(o -> o.getPaidAmount() != null)
                .map(PurchaseOrder::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 构建统计结果
        return Map.of(
                "totalCount", totalCount,
                "approvalStats", Map.of(
                        "draft", draftCount,
                        "pending", pendingCount,
                        "approved", approvedCount,
                        "rejected", rejectedCount,
                        "cancelled", cancelledCount
                ),
                "receiptStats", Map.of(
                        "pending", pendingReceiptCount,
                        "partiallyReceived", partiallyReceivedCount,
                        "completed", completedReceiptCount
                ),
                "paymentStats", Map.of(
                        "pending", pendingPaymentCount,
                        "partiallyPaid", partiallyPaidCount,
                        "completed", completedPaymentCount
                ),
                "urgentCount", urgentCount,
                "totalAmount", totalAmount,
                "totalPaid", totalPaid,
                "unpaidAmount", totalAmount.subtract(totalPaid)
        );
    }

    @Override
    public String generateOrderNo() {
        // 生成订单号：PO + 日期(yyyyMMdd) + 4位序号
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PO" + dateStr;

        // 查询当天最大序号
        LambdaQueryWrapper<PurchaseOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.likeRight(PurchaseOrder::getOrderNo, prefix);
        wrapper.orderByDesc(PurchaseOrder::getOrderNo);
        wrapper.last("LIMIT 1");
        List<PurchaseOrder> lastOrders = orderMapper.selectList(wrapper);

        int seq = 1;
        if (!lastOrders.isEmpty()) {
            String lastOrderNo = lastOrders.get(0).getOrderNo();
            String seqStr = lastOrderNo.substring(prefix.length());
            try {
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }

        return prefix + StringUtils.leftPad(String.valueOf(seq), 4, "0");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyOrder(Long sourceOrderId) {
        // 查询源订单
        PurchaseOrder sourceOrder = orderMapper.selectById(sourceOrderId);
        if (sourceOrder == null) {
            throw new BusinessException(PurchaseExceptionEnum.SOURCE_ORDER_NOT_FOUND.getMessage());
        }

        // 查询源订单明细
        LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = Wrappers.lambdaQuery();
        itemWrapper.eq(PurchaseOrderItem::getOrderId, sourceOrderId);
        List<PurchaseOrderItem> sourceItems = orderItemMapper.selectList(itemWrapper);

        // 创建新订单
        PurchaseOrder newOrder = new PurchaseOrder();
        newOrder.setOrderNo(generateOrderNo());
        newOrder.setSupplierId(sourceOrder.getSupplierId());
        newOrder.setSupplierName(sourceOrder.getSupplierName());
        newOrder.setOrderType(sourceOrder.getOrderType());
        newOrder.setOrderDate(LocalDate.now());
        newOrder.setExpectedDeliveryDate(sourceOrder.getExpectedDeliveryDate());
        newOrder.setCurrency(sourceOrder.getCurrency());
        newOrder.setOrderAmount(sourceOrder.getOrderAmount());
        newOrder.setOrderTax(sourceOrder.getOrderTax());
        newOrder.setOrderTotalAmount(sourceOrder.getOrderTotalAmount());
        newOrder.setApprovalStatus(ApprovalStatusEnum.DRAFT.getCode());
        newOrder.setReceiptStatus(ReceiptStatusEnum.PENDING.getCode());
        newOrder.setPaymentStatus(PaymentStatusEnum.PENDING.getCode());
        newOrder.setPaidAmount(BigDecimal.ZERO);
        newOrder.setContractNo(sourceOrder.getContractNo());
        newOrder.setDeliveryMethod(sourceOrder.getDeliveryMethod());
        newOrder.setDeliveryAddress(sourceOrder.getDeliveryAddress());
        newOrder.setRemark(sourceOrder.getRemark());
        newOrder.setUrgentFlag(sourceOrder.getUrgentFlag());
        newOrder.setUrgentReason(sourceOrder.getUrgentReason());

        // 保存新订单
        int result = orderMapper.insert(newOrder);
        if (result <= 0) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_COPY_FAILED.getMessage());
        }

        // 复制订单明细
        for (PurchaseOrderItem sourceItem : sourceItems) {
            PurchaseOrderItem newItem = new PurchaseOrderItem();
            newItem.setOrderId(newOrder.getOrderId());
            newItem.setMaterialId(sourceItem.getMaterialId());
            newItem.setMaterialCode(sourceItem.getMaterialCode());
            newItem.setMaterialName(sourceItem.getMaterialName());
            newItem.setMaterialSpec(sourceItem.getMaterialSpec());
            newItem.setUnit(sourceItem.getUnit());
            newItem.setQuantity(sourceItem.getQuantity());
            newItem.setUnitPrice(sourceItem.getUnitPrice());
            newItem.setAmount(sourceItem.getAmount());
            newItem.setReceivedQuantity(BigDecimal.ZERO);
            newItem.setReceiptStatus(ReceiptStatusEnum.PENDING.getCode());
            newItem.setInquiryInfo(sourceItem.getInquiryInfo());
            newItem.setInquiryStatus(sourceItem.getInquiryStatus());
            newItem.setBatchNo(sourceItem.getBatchNo());
            newItem.setProductionDate(sourceItem.getProductionDate());
            newItem.setExpiryDate(sourceItem.getExpiryDate());
            newItem.setInspectionResult(sourceItem.getInspectionResult());
            newItem.setInspectionRemark(sourceItem.getInspectionRemark());
            newItem.setItemOrder(sourceItem.getItemOrder());

            orderItemMapper.insert(newItem);
        }

        return newOrder.getOrderId();
    }

    @Override
    public List<PurchaseOrderItemVO> selectOrderItemsById(Long orderId) {
        LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = Wrappers.lambdaQuery();
        itemWrapper.eq(PurchaseOrderItem::getOrderId, orderId);
        itemWrapper.orderByAsc(PurchaseOrderItem::getItemOrder);
        List<PurchaseOrderItem> items = orderItemMapper.selectList(itemWrapper);
        return purchaseConverter.toItemVOList(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        // 检查订单是否存在
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 检查订单状态是否允许删除（只有草稿可删除）
        ApprovalStatusEnum current = ApprovalStatusEnum.getByCode(order.getApprovalStatus());
        if (!current.isCancelable()) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_DELETABLE.getMessage());
        }
        POrderStatusDTO dto = POrderStatusDTO.builder().orderId(orderId).currentStatus(current.getCode()).targetStatus(ApprovalStatusEnum.CANCELLED.getCode()).build();
        updateOrderStatus(dto);
    }

    @Override
    public void updateOrderStatus(POrderStatusDTO dto) {
        LambdaUpdateWrapper<PurchaseOrder> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(PurchaseOrder::getApprovalStatus,dto.getTargetStatus())
                .eq(PurchaseOrder::getOrderId,dto.getOrderId())
                .eq(PurchaseOrder::getApprovalStatus,dto.getCurrentStatus());
        orderMapper.update(updateWrapper);
    }

    /**
     * 计算订单金额
     */
    private static void calculateOrderAmount(PurchaseOrderDTO orderDTO) {
        BigDecimal orderAmount = BigDecimal.ZERO;
        BigDecimal orderTax = BigDecimal.ZERO;

        for (PurchaseOrderItemDTO itemDTO : orderDTO.getItems()) {
            // 计算明细项金额
            BigDecimal quantity = itemDTO.getQuantity() != null ? itemDTO.getQuantity() : BigDecimal.ZERO;
            BigDecimal unitPrice = itemDTO.getUnitPrice() != null ? itemDTO.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal amount = quantity.multiply(unitPrice);
            itemDTO.setAmount(amount);

            // 计算明细项税额
            BigDecimal taxRate = itemDTO.getTaxRate() != null ? itemDTO.getTaxRate() : BigDecimal.ZERO;
            BigDecimal taxAmount = amount.multiply(taxRate).divide(BigDecimal.valueOf(100));
            itemDTO.setTaxAmount(taxAmount);

            // 累加订单金额和税额
            orderAmount = orderAmount.add(amount);
            orderTax = orderTax.add(taxAmount);
        }

        // 设置订单金额
        orderDTO.setOrderAmount(orderAmount);
        orderDTO.setOrderTax(orderTax);
        orderDTO.setOrderTotalAmount(orderAmount.add(orderTax));
    }

    /**
     * 保存订单明细
     */
    private void saveOrderItems(Long orderId, List<PurchaseOrderItemDTO> itemDTOs) {
        for (int i = 0; i < itemDTOs.size(); i++) {
            PurchaseOrderItemDTO itemDTO = itemDTOs.get(i);
            PurchaseOrderItem item = purchaseConverter.toEntity(itemDTO);
            item.setOrderId(orderId);
            item.setItemOrder(i + 1);

            // 设置默认值
            if (item.getReceivedQuantity() == null) {
                item.setReceivedQuantity(BigDecimal.ZERO);
            }
            if (item.getReceiptStatus() == null) {
                item.setReceiptStatus(ReceiptStatusEnum.PENDING.getCode());
            }
            if (item.getInquiryStatus() == null) {
                item.setInquiryStatus(InquiryStatus.PENDING.getCode());
            }

            orderItemMapper.insert(item);
        }
    }

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<PurchaseOrder> buildQueryWrapper(PurchaseOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = Wrappers.lambdaQuery();

        if (StringUtils.isNotEmpty(queryDTO.getOrderNo())) {
            wrapper.like(PurchaseOrder::getOrderNo, queryDTO.getOrderNo());
        }
        if (StringUtils.isNotEmpty(queryDTO.getSupplierName())) {
            wrapper.like(PurchaseOrder::getSupplierName, queryDTO.getSupplierName());
        }
        if (queryDTO.getApprovalStatus() != null) {
            wrapper.eq(PurchaseOrder::getApprovalStatus, queryDTO.getApprovalStatus());
        }
        if (queryDTO.getReceiptStatus() != null) {
            wrapper.eq(PurchaseOrder::getReceiptStatus, queryDTO.getReceiptStatus());
        }
        if (queryDTO.getPaymentStatus() != null) {
            wrapper.eq(PurchaseOrder::getPaymentStatus, queryDTO.getPaymentStatus());
        }

        wrapper.orderByDesc(PurchaseOrder::getCreateTime);
        return wrapper;
    }

    /**
     * 生成导出文件路径
     */
    private static String generateExportFilePath(String fileName) {
        String exportDir = System.getProperty("java.io.tmpdir") + "/purchase_export/";
        java.io.File dir = new java.io.File(exportDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return exportDir + fileName + "_" + System.currentTimeMillis() + ".xlsx";
    }

    /**
     * 写入导出表头
     */
    private static <T> void writeExportHeader(Sheet sheet, Class<T> clazz) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        List<Field> fields = getExportFields(clazz);
        for (int i = 0; i < fields.size(); i++) {
            Cell cell = headerRow.createCell(i);
            ExcelColumn annotation = fields.get(i).getAnnotation(ExcelColumn.class);
            cell.setCellValue(annotation.value());
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * 写入导出数据
     */
    private static <T> void writeExportData(Sheet sheet, List<T> dataList, Class<T> clazz) {
        CellStyle dataStyle = createDataStyle(sheet.getWorkbook());
        List<Field> fields = getExportFields(clazz);

        for (int rowIndex = 0; rowIndex < dataList.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            T item = dataList.get(rowIndex);

            for (int colIndex = 0; colIndex < fields.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                try {
                    Field field = fields.get(colIndex);
                    field.setAccessible(true);
                    Object value = field.get(item);
                    setCellValue(cell, value);
                    cell.setCellStyle(dataStyle);
                } catch (Exception e) {
                    cell.setCellValue("");
                }
            }
        }
    }

    /**
     * 自动调整列宽
     */
    private static <T> void autoSizeColumns(Sheet sheet, Class<T> clazz) {
        List<Field> fields = getExportFields(clazz);
        for (int i = 0; i < fields.size(); i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            if (width < 3000) {
                sheet.setColumnWidth(i, 3000);
            } else if (width > 15000) {
                sheet.setColumnWidth(i, 15000);
            }
        }
    }

    /**
     * 获取导出字段列表（按ExcelColumn注解排序）
     */
    private static <T> List<Field> getExportFields(Class<T> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ExcelColumn.class)) {
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparingInt(f -> f.getAnnotation(ExcelColumn.class).order()));
        return fields;
    }

    /**
     * 创建表头样式
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 创建数据样式
     */
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 设置单元格值
     */
    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof LocalDate) {
            cell.setCellValue(value.toString());
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(value.toString());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "purchase.received", bizId = "#dto.orderId", bizType = "'purchase'")
    public int batchReceiveOrderItems(PurchaseOrderReceiveDTO dto) {
        // 检查订单是否存在
        PurchaseOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }

        // 检查订单状态（已批准或待审批可收货）
        Integer status = order.getApprovalStatus();
        if (!Objects.equals(ApprovalStatusEnum.PENDING.getCode(), status)
                && !Objects.equals(ApprovalStatusEnum.APPROVED.getCode(), status)) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_RECEIVABLE.getMessage());
        }

        int totalCount = 0;
        for (PurchaseOrderReceiveDTO.ReceiveItemDTO itemDTO : dto.getItems()) {
            // 检查明细项
            PurchaseOrderItem item = orderItemMapper.selectById(itemDTO.getItemId());
            if (item == null) {
                throw new BusinessException(PurchaseExceptionEnum.ORDER_ITEM_NOT_FOUND.getMessage() + ": itemId=" + itemDTO.getItemId());
            }

            // 更新收货数量
            BigDecimal newReceivedQuantity = item.getReceivedQuantity() != null ?
                    item.getReceivedQuantity().add(itemDTO.getReceivedQuantity()) : itemDTO.getReceivedQuantity();
            item.setReceivedQuantity(newReceivedQuantity);

            // 更新检验结果
            if (StringUtils.isNotEmpty(itemDTO.getInspectionResult())) {
                item.setInspectionResult(itemDTO.getInspectionResult());
            }
            if (StringUtils.isNotEmpty(itemDTO.getInspectionRemark())) {
                item.setInspectionRemark(itemDTO.getInspectionRemark());
            }

            // 更新收货状态
            if (newReceivedQuantity.compareTo(item.getQuantity()) >= 0) {
                item.setReceiptStatus(ReceiptStatusEnum.COMPLETED.getCode());
            } else if (newReceivedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                item.setReceiptStatus(ReceiptStatusEnum.PARTIALLY_RECEIVED.getCode());
            }

            orderItemMapper.updateById(item);
            totalCount++;
        }

        // 更新订单整体收货状态
        updateOrderReceiptStatus(dto.getOrderId());

        return totalCount;
    }

    /**
     * 更新订单整体收货状态
     */
    private void updateOrderReceiptStatus(Long orderId) {
        // 查询订单所有明细
        LambdaQueryWrapper<PurchaseOrderItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchaseOrderItem::getOrderId, orderId);
        List<PurchaseOrderItem> items = orderItemMapper.selectList(wrapper);

        if (items.isEmpty()) {
            return;
        }

        // 统计收货状态
        long totalItems = items.size();
        long completedItems = items.stream()
                .filter(item -> Objects.equals(ReceiptStatusEnum.COMPLETED.getCode(), item.getReceiptStatus()))
                .count();
        long partiallyReceivedItems = items.stream()
                .filter(item -> Objects.equals(ReceiptStatusEnum.PARTIALLY_RECEIVED.getCode(), item.getReceiptStatus()))
                .count();

        // 更新订单收货状态
        Integer receiptStatus;
        if (completedItems == totalItems) {
            receiptStatus = ReceiptStatusEnum.COMPLETED.getCode();
        } else if (completedItems > 0 || partiallyReceivedItems > 0) {
            receiptStatus = ReceiptStatusEnum.PARTIALLY_RECEIVED.getCode();
        } else {
            receiptStatus = ReceiptStatusEnum.PENDING.getCode();
        }

        orderMapper.updateReceiptStatus(orderId, receiptStatus);
    }

    @Override
    public void returnGoods(Long orderId, String reason, Long materialId, Integer quantity) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(PurchaseExceptionEnum.ORDER_NOT_FOUND.getMessage());
        }
        log.info("采购退货: orderId={}, reason={}, materialId={}, quantity={}", orderId, reason, materialId, quantity);

        // 记录退货状态
        order.setRemark("退货: " + reason);
        orderMapper.updateById(order);

        // 退货扣库存（DEV-352 补全）：按 FIFO 扣减 + 写流水
        if (materialId != null && quantity != null && quantity > 0) {
            java.math.BigDecimal remaining = java.math.BigDecimal.valueOf(quantity);
            java.util.List<com.jjx.inventory.domain.InventoryStockItem> fifoItems =
                    stockItemMapper.selectFIFOAvailable(materialId);
            if (fifoItems == null || fifoItems.isEmpty()) {
                throw new BusinessException("物料无可用库存，无法退货");
            }
            for (com.jjx.inventory.domain.InventoryStockItem si : fifoItems) {
                if (remaining.compareTo(java.math.BigDecimal.ZERO) <= 0) break;
                java.math.BigDecimal deductQty = remaining.min(
                        si.getQuantity().subtract(si.getReservedQuantity() == null ? java.math.BigDecimal.ZERO : si.getReservedQuantity()));
                if (deductQty.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                stockItemMapper.deductStock(si.getItemId(), deductQty);
                remaining = remaining.subtract(deductQty);
            }
            if (remaining.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new BusinessException("物料库存不足，缺少: " + remaining);
            }
            stockMapper.refreshSummary(materialId);

            // 写库存流水
            try {
                com.jjx.inventory.domain.InventoryStock currentStock = stockMapper.selectByMaterialId(materialId);
                java.math.BigDecimal beforeQty = java.math.BigDecimal.ZERO;
                if (currentStock != null && currentStock.getTotalQuantity() != null) {
                    beforeQty = currentStock.getTotalQuantity().add(java.math.BigDecimal.valueOf(quantity));
                }
                com.jjx.inventory.domain.InventoryTransaction tx = new com.jjx.inventory.domain.InventoryTransaction();
                tx.setMaterialId(materialId);
                tx.setTransactionType("RETURN");
                tx.setSourceType("purchase_return");
                tx.setSourceId(orderId);
                tx.setSourceNo(order.getOrderNo());
                tx.setQuantity(java.math.BigDecimal.valueOf(quantity).negate());
                tx.setBeforeQuantity(beforeQty);
                tx.setAfterQuantity(beforeQty.subtract(java.math.BigDecimal.valueOf(quantity)));
                tx.setTransactionTime(java.time.LocalDateTime.now());
                tx.setOperatorId(com.jjx.system.utils.SecurityUtils.getUserId());
                tx.setOperatorName(com.jjx.system.utils.SecurityUtils.getUsername());
                tx.setRemark("采购退货扣减: " + reason);
                transactionMapper.insert(tx);
            } catch (Exception e) {
                log.warn("写退货库存流水失败: {}", e.getMessage());
            }
            log.info("采购退货扣库存完成: materialId={}, qty={}", materialId, quantity);
        }

        log.info("采购退货成功: orderId={}", orderId);
    }
}
