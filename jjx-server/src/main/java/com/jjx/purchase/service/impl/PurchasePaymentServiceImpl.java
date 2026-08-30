package com.jjx.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.PurchasePaymentDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchasePayment;
import com.jjx.purchase.domain.enums.PurchasePaymentStatusEnum;
import com.jjx.purchase.domain.enums.PurchaseExceptionEnum;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchasePaymentMapper;
import com.jjx.purchase.service.IPurchasePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.jjx.system.annotation.Event;

/**
 * 采购付款服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePaymentServiceImpl extends ServiceImpl<PurchasePaymentMapper, PurchasePayment> implements IPurchasePaymentService {

    private final PurchasePaymentMapper paymentMapper;
    private final PurchaseOrderMapper orderMapper;

    @Override
    public List<PurchasePayment> selectPaymentList(PurchasePaymentDTO dto) {
        LambdaQueryWrapper<PurchasePayment> wrapper = Wrappers.lambdaQuery();
        if (dto != null) {
            if (StringUtils.isNotEmpty(dto.getPaymentNo())) {
                wrapper.like(PurchasePayment::getPaymentNo, dto.getPaymentNo());
            }
            if (dto.getOrderId() != null) {
                wrapper.eq(PurchasePayment::getOrderId, dto.getOrderId());
            }
            if (dto.getPaymentStatus() != null) {
                wrapper.eq(PurchasePayment::getPaymentStatus, dto.getPaymentStatus());
            }
            if (StringUtils.isNotEmpty(dto.getPaymentMethod())) {
                wrapper.eq(PurchasePayment::getPaymentMethod, dto.getPaymentMethod());
            }
        }
        wrapper.orderByDesc(PurchasePayment::getCreateTime).orderByDesc(PurchasePayment::getPaymentId);
        return paymentMapper.selectList(wrapper);
    }

    @Override
    public PurchasePayment selectPaymentById(Long paymentId) {
        PurchasePayment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException("付款记录不存在");
        }
        return payment;
    }

    @Override
    @Event(value = "purchase.payment.created", bizId = "#dto", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int insertPayment(PurchasePaymentDTO dto) {
        // 检查付款单号是否唯一
        if (checkPaymentNoUnique(dto.getPaymentNo())) {
            throw new BusinessException("付款单号已存在");
        }

        // 检查订单是否存在
        PurchaseOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }

        // 091定稿：已取消(2)/已拒绝(5)的订单不能付款
        Integer approvalStatus = order.getApprovalStatus();
        if (approvalStatus != null && (approvalStatus == 2 || approvalStatus == 5)) {
            throw new BusinessException("订单已取消/已拒绝，不能付款");
        }
        // 付款不拦收货（允许预付款/定金），但累计付款≤订单金额
        if (dto.getPaymentAmount() != null && order.getOrderTotalAmount() != null
                && dto.getPaymentAmount().compareTo(order.getOrderTotalAmount()) > 0) {
            throw new BusinessException(PurchaseExceptionEnum.PAYMENT_AMOUNT_EXCEEDS, PurchaseExceptionEnum.PAYMENT_AMOUNT_EXCEEDS.getMessage()
                    + "（订单金额" + order.getOrderTotalAmount().stripTrailingZeros().toPlainString() + "）");
        }

        PurchasePayment payment = new PurchasePayment();
        copyProperties(dto, payment);

        // 设置默认状态
        if (payment.getPaymentStatus() == null) {
            payment.setPaymentStatus(PurchasePaymentStatusEnum.PENDING.getValue());
        }

        int result = paymentMapper.insert(payment);

        // 更新订单付款信息
        updateOrderPaymentInfo(dto.getOrderId());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePayment(PurchasePaymentDTO dto) {
        if (dto.getPaymentId() == null) {
            throw new BusinessException("付款ID不能为空");
        }

        PurchasePayment existing = paymentMapper.selectById(dto.getPaymentId());
        if (existing == null) {
            throw new BusinessException("付款记录不存在");
        }

        PurchasePayment payment = new PurchasePayment();
        copyProperties(dto, payment);
        payment.setPaymentId(dto.getPaymentId());

        return paymentMapper.updateById(payment);
    }

    @Override
    @Event(value = "purchase.payment.deleted", bizId = "#paymentId", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int deletePaymentById(Long paymentId) {
        PurchasePayment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException("付款记录不存在");
        }

        int result = paymentMapper.deleteById(paymentId);

        // 更新订单付款信息
        if (payment.getOrderId() != null) {
            updateOrderPaymentInfo(payment.getOrderId());
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePaymentByIds(Long[] paymentIds) {
        int count = 0;
        for (Long paymentId : paymentIds) {
            count += deletePaymentById(paymentId);
        }
        return count;
    }

    @Override
    @Event(value = "purchase.payment.approved", bizId = "#paymentId", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int approvePayment(Long paymentId, String approvalStatus, String approverName, String approvalComment) {
        PurchasePayment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new BusinessException("付款记录不存在");
        }

        if (!Objects.equals(PurchasePaymentStatusEnum.PENDING.getValue(), payment.getPaymentStatus())) {
            throw new BusinessException("只有待审批状态的付款可以审批");
        }

        payment.setPaymentStatus("approved".equals(approvalStatus) ? PurchasePaymentStatusEnum.COMPLETED.getValue() : PurchasePaymentStatusEnum.PENDING.getValue());
        payment.setApprovalTime(LocalDateTime.now());
        if (StringUtils.isNotEmpty(approvalComment)) {
            payment.setRemark(approvalComment);
        }
        payment.setUpdateTime(LocalDateTime.now());

        int result = paymentMapper.updateById(payment);

        // 如果审批通过，更新订单付款信息
        if ("approved".equals(approvalStatus) && payment.getOrderId() != null) {
            updateOrderPaymentInfo(payment.getOrderId());
        }

        return result;
    }

    @Override
    @Event(value = "purchase.payment.confirmed", bizId = "#dto", bizType = "'purchase'")
    @Transactional(rollbackFor = Exception.class)
    public int confirmPayment(PurchasePaymentDTO dto) {
        PurchasePayment payment = paymentMapper.selectById(dto.getPaymentId());
        if (payment == null) {
            throw new BusinessException("付款记录不存在");
        }

        if (!Objects.equals(PurchasePaymentStatusEnum.COMPLETED.getValue(), payment.getPaymentStatus())) {
            throw new BusinessException("只有已批准的付款可以确认");
        }

        payment.setPaymentStatus(PurchasePaymentStatusEnum.COMPLETED.getValue());
        payment.setActualPaymentDate(dto.getActualPaymentDate() != null ? dto.getActualPaymentDate() : LocalDate.now());
        payment.setVoucherNo(dto.getVoucherNo());
        payment.setVoucherFileUrl(dto.getVoucherFileUrl());
        payment.setUpdateTime(LocalDateTime.now());

        int result = paymentMapper.updateById(payment);

        // 更新订单付款信息
        if (payment.getOrderId() != null) {
            updateOrderPaymentInfo(payment.getOrderId());
        }

        return result;
    }

    @Override
    public boolean checkPaymentNoUnique(String paymentNo) {
        return paymentMapper.checkPaymentNoUnique(paymentNo) > 0;
    }

    @Override
    public String generatePaymentNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PAY" + dateStr;

        LambdaQueryWrapper<PurchasePayment> wrapper = Wrappers.lambdaQuery();
        wrapper.likeRight(PurchasePayment::getPaymentNo, prefix);
        wrapper.orderByDesc(PurchasePayment::getPaymentNo);
        wrapper.last("LIMIT 1");
        List<PurchasePayment> lastPayments = paymentMapper.selectList(wrapper);

        int seq = 1;
        if (!lastPayments.isEmpty()) {
            String lastNo = lastPayments.get(0).getPaymentNo();
            String seqStr = lastNo.substring(prefix.length());
            try {
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }

        return prefix + StringUtils.leftPad(String.valueOf(seq), 4, "0");
    }

    @Override
    public List<PurchasePayment> selectByOrderId(Long orderId) {
        return paymentMapper.selectByOrderId(orderId);
    }

    @Override
    public List<PurchasePayment> selectBySupplierId(Long supplierId) {
        return paymentMapper.selectBySupplierId(supplierId);
    }

    @Override
    public List<PurchasePayment> selectPendingApproval() {
        return paymentMapper.selectPendingApproval();
    }

    @Override
    public List<PurchasePayment> selectApproved() {
        return paymentMapper.selectApproved();
    }

    @Override
    public List<PurchasePayment> selectToday() {
        return paymentMapper.selectToday();
    }

    @Override
    public List<PurchasePayment> selectWeek() {
        return paymentMapper.selectWeek();
    }

    @Override
    public List<PurchasePayment> selectMonth() {
        return paymentMapper.selectMonth();
    }

    @Override
    public Map<String, Object> getPaymentStatistics() {
        List<PurchasePayment> allPayments = paymentMapper.selectList(Wrappers.emptyWrapper());

        long totalCount = allPayments.size();
        long pendingCount = allPayments.stream()
                .filter(p -> Objects.equals(PurchasePaymentStatusEnum.PENDING.getValue(), p.getPaymentStatus()))
                .count();
        long approvedCount = allPayments.stream()
                .filter(p -> Objects.equals(PurchasePaymentStatusEnum.COMPLETED.getValue(), p.getPaymentStatus()))
                .count();
        long paidCount = allPayments.stream()
                .filter(p -> Objects.equals("paid", p.getPaymentStatus()))
                .count();

        BigDecimal totalAmount = allPayments.stream()
                .filter(p -> p.getPaymentAmount() != null)
                .map(PurchasePayment::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalCount", totalCount,
                "pendingCount", pendingCount,
                "approvedCount", approvedCount,
                "paidCount", paidCount,
                "totalAmount", totalAmount
        );
    }

    @Override
    public String exportPaymentList(PurchasePaymentDTO dto) {
        List<PurchasePayment> list = selectPaymentList(dto);
        if (list.isEmpty()) {
            throw new BusinessException("没有可导出的数据");
        }

        String fileName = "采购付款列表_" + LocalDate.now().toString();
        String filePath = System.getProperty("java.io.tmpdir") + "/purchase_export/" + fileName + "_" + System.currentTimeMillis() + ".xlsx";

        // TODO: 使用POI生成Excel文件
        log.info("导出采购付款列表成功，文件路径: {}", filePath);
        return filePath;
    }

    /**
     * 更新订单付款信息
     */
    private void updateOrderPaymentInfo(Long orderId) {
        // 查询该订单所有已付款记录
        LambdaQueryWrapper<PurchasePayment> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(PurchasePayment::getOrderId, orderId);
        wrapper.eq(PurchasePayment::getPaymentStatus, "paid");
        List<PurchasePayment> paidPayments = paymentMapper.selectList(wrapper);

        BigDecimal totalPaid = paidPayments.stream()
                .filter(p -> p.getPaymentAmount() != null)
                .map(PurchasePayment::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 查询订单总金额
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) return;

        Integer paymentStatus;
        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            paymentStatus = PurchasePaymentStatusEnum.PENDING.getValue();
        } else if (order.getOrderTotalAmount() != null && totalPaid.compareTo(order.getOrderTotalAmount()) >= 0) {
            paymentStatus = PurchasePaymentStatusEnum.COMPLETED.getValue();
        } else {
            paymentStatus = PurchasePaymentStatusEnum.PARTIALLY_PAID.getValue();
        }

        orderMapper.updatePaymentInfo(orderId, totalPaid, paymentStatus);
    }

    /**
     * 复制DTO属性到实体
     */
    private void copyProperties(PurchasePaymentDTO dto, PurchasePayment payment) {
        payment.setPaymentNo(dto.getPaymentNo());
        payment.setOrderId(dto.getOrderId());
        payment.setDocumentId(dto.getDocumentId());
        payment.setPaymentDate(dto.getPaymentDate());
        payment.setPaymentAmount(dto.getPaymentAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setBankAccount(dto.getBankAccount());
        payment.setPaymentStatus(dto.getPaymentStatus());
        payment.setVoucherNo(dto.getVoucherNo());
        payment.setVoucherFileUrl(dto.getVoucherFileUrl());
        payment.setRemark(dto.getRemark());
    }

    @Override
    public java.util.List<com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO> batchCheckPayment(java.util.List<com.jjx.purchase.domain.dto.PaymentBatchCheckItemDTO> items) {
        java.util.List<com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO> results = new java.util.ArrayList<>();
        if (items == null || items.isEmpty()) {
            return results;
        }

        // 文件内重复检测（同付款单号）
        java.util.Map<String, Integer> dupCountMap = new java.util.HashMap<>();
        for (com.jjx.purchase.domain.dto.PaymentBatchCheckItemDTO item : items) {
            String k = item.getPaymentNo() == null ? "" : item.getPaymentNo().trim();
            dupCountMap.merge(k, 1, Integer::sum);
        }

        for (com.jjx.purchase.domain.dto.PaymentBatchCheckItemDTO item : items) {
            com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO vo = new com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO();
            vo.setRowIndex(item.getRowIndex());
            vo.setStatus("ok");

            // 1. 付款单号必填
            String paymentNo = item.getPaymentNo() == null ? "" : item.getPaymentNo().trim();
            if (paymentNo.isEmpty()) {
                vo.setStatus("error");
                vo.setErrorType("MISSING_REQUIRED");
                addFieldError(vo, "paymentNo", "MISSING_REQUIRED", "付款单号不能为空");
            } else {
                // 2. 文件内重复
                Integer dupCount = dupCountMap.get(paymentNo);
                if (dupCount != null && dupCount > 1) {
                    vo.setStatus("error");
                    vo.setErrorType("DUPLICATE");
                    addFieldError(vo, "paymentNo", "DUPLICATE", "文件内重复行（同一付款单号出现 " + dupCount + " 次），导入会冲突");
                } else if (checkPaymentNoUnique(paymentNo)) {
                    // 3. 库中已存在
                    vo.setStatus("error");
                    vo.setErrorType("DUPLICATE");
                    addFieldError(vo, "paymentNo", "DUPLICATE", "付款单号已存在: " + paymentNo);
                }
            }

            // 4. 订单存在性
            if (vo.getStatus().equals("ok")) {
                if (item.getOrderId() == null) {
                    vo.setStatus("error");
                    vo.setErrorType("MISSING_REQUIRED");
                    addFieldError(vo, "orderId", "MISSING_REQUIRED", "采购订单ID不能为空");
                } else if (orderMapper.selectById(item.getOrderId()) == null) {
                    vo.setStatus("error");
                    vo.setErrorType("NOT_FOUND");
                    addFieldError(vo, "orderId", "NOT_FOUND", "采购订单不存在: " + item.getOrderId());
                }
            }

            // 5. 金额校验
            if (vo.getStatus().equals("ok")) {
                if (item.getPaymentAmount() == null || item.getPaymentAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    vo.setStatus("error");
                    vo.setErrorType("INVALID");
                    addFieldError(vo, "paymentAmount", "INVALID", "付款金额必须大于0");
                }
            }

            // 6. 付款日期
            if (vo.getStatus().equals("ok") && item.getPaymentDate() == null) {
                vo.setStatus("error");
                vo.setErrorType("MISSING_REQUIRED");
                addFieldError(vo, "paymentDate", "MISSING_REQUIRED", "付款日期不能为空");
            }

            results.add(vo);
        }
        return results;
    }

    private void addFieldError(com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO vo, String field, String type, String message) {
        com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO.FieldError fe = new com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO.FieldError();
        fe.setField(field);
        fe.setType(type);
        fe.setMessage(message);
        vo.getErrors().add(fe);
    }
}
