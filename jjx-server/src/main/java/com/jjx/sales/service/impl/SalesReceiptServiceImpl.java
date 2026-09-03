package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesReceipt;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SalesPaymentStatusEnum;
import com.jjx.sales.enums.SalesReceiptStatusEnum;
import com.jjx.sales.mapper.SalesReceiptMapper;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.service.SalesReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesReceiptServiceImpl extends ServiceImpl<SalesReceiptMapper, SalesReceipt> implements SalesReceiptService {
    private static final Integer VOID_RECEIPT_STATUS = SalesReceiptStatusEnum.VOID.getValue();
    private static final Integer NORMAL_RECEIPT_STATUS = SalesReceiptStatusEnum.NORMAL.getValue();

    private final SalesReceiptMapper receiptMapper;
    private final OrderMapper orderMapper;

    @Override public PageResult<SalesReceipt> page(int pageNum, int pageSize, String receiptNo, String customerName,
                                                   java.time.LocalDate startDate, java.time.LocalDate endDate, Integer status) {
        LambdaQueryWrapper<SalesReceipt> query = new LambdaQueryWrapper<SalesReceipt>()
                .like(receiptNo != null && !receiptNo.isBlank(), SalesReceipt::getReceiptNo, receiptNo)
                .like(customerName != null && !customerName.isBlank(), SalesReceipt::getCustomerName, customerName)
                .ge(startDate != null, SalesReceipt::getReceiptDate, startDate)
                .le(endDate != null, SalesReceipt::getReceiptDate, endDate)
                .eq(status != null, SalesReceipt::getStatus, status)
                .orderByDesc(SalesReceipt::getCreateTime).orderByDesc(SalesReceipt::getReceiptId);
        Page<SalesReceipt> p = receiptMapper.selectPage(new Page<>(pageNum, pageSize),
                query);
        return PageResult.of(p, p.getRecords());
    }
    @Override public SalesReceipt getById(Long id) { return receiptMapper.selectById(id); }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalesReceipt receipt) {
        // DEV-934修复：actual_amount NOT NULL 无默认值，前端未传时兜底 = receiptAmount
        if (receipt.getActualAmount() == null) {
            receipt.setActualAmount(receipt.getReceiptAmount());
        }
        receiptMapper.insert(receipt);
        if (receipt.getOrderId() != null && !VOID_RECEIPT_STATUS.equals(receipt.getStatus())) {
            updateOrderPaymentStatus(receipt.getOrderId());
        }
        return receipt.getReceiptId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalesReceipt receipt) {
        SalesReceipt oldReceipt = receiptMapper.selectById(receipt.getReceiptId());
        if (oldReceipt == null) {
            return false;
        }
        if (receipt.getActualAmount() == null) {
            receipt.setActualAmount(receipt.getReceiptAmount());
        }
        if (receipt.getStatus() == null) {
            receipt.setStatus(oldReceipt.getStatus());
        }
        boolean updated = receiptMapper.updateById(receipt) > 0;
        if (!updated) {
            return false;
        }
        if (receipt.getOrderId() != null && !VOID_RECEIPT_STATUS.equals(receipt.getStatus())) {
            updateOrderPaymentStatus(receipt.getOrderId());
        }
        if (oldReceipt.getOrderId() != null && !oldReceipt.getOrderId().equals(receipt.getOrderId())) {
            updateOrderPaymentStatus(oldReceipt.getOrderId());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        SalesReceipt oldReceipt = receiptMapper.selectById(id);
        if (oldReceipt == null) {
            return false;
        }
        boolean deleted = receiptMapper.deleteById(id) > 0;
        if (!deleted) {
            return false;
        }
        if (oldReceipt.getOrderId() != null) {
            updateOrderPaymentStatus(oldReceipt.getOrderId());
        }
        return true;
    }

    private void updateOrderPaymentStatus(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("收款单关联的销售订单不存在，跳过付款状态回写: orderId={}", orderId);
            return;
        }

        List<SalesReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<SalesReceipt>()
                .eq(SalesReceipt::getOrderId, orderId)
                .eq(SalesReceipt::getStatus, NORMAL_RECEIPT_STATUS));
        BigDecimal paid = receipts.stream()
                .map(item -> item.getActualAmount() != null ? item.getActualAmount() : item.getReceiptAmount())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal target = order.getFinalAmount() != null
                ? order.getFinalAmount()
                : (order.getTotalAmountWithTax() != null ? order.getTotalAmountWithTax() : order.getTotalAmount());
        Integer paymentStatus = paid.compareTo(BigDecimal.ZERO) <= 0
                ? SalesPaymentStatusEnum.UNPAID.getValue()
                : target != null && paid.compareTo(target) >= 0
                        ? SalesPaymentStatusEnum.PAID.getValue()
                        : SalesPaymentStatusEnum.PARTIAL_PAID.getValue();

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setPaymentStatus(paymentStatus);
        if (target != null) {
            update.setPaidAmount(paid);
            update.setUnpaidAmount(target.subtract(paid).max(BigDecimal.ZERO));
        }
        orderMapper.updateById(update);
    }
}
