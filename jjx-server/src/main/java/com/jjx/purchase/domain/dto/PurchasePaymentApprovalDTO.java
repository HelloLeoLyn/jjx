package com.jjx.purchase.domain.dto;

/** 批量审批只接收决定与意见；审批人始终取可信登录会话。 */
public record PurchasePaymentApprovalDTO(Long paymentId, String approvalStatus, String approvalComment) {
}
