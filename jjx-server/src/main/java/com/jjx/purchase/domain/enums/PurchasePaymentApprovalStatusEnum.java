package com.jjx.purchase.domain.enums;

import lombok.Getter;

@Getter
public enum PurchasePaymentApprovalStatusEnum {
    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String label;

    PurchasePaymentApprovalStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static PurchasePaymentApprovalStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (PurchasePaymentApprovalStatusEnum status : values()) {
            if (status.code.equalsIgnoreCase(code)) return status;
        }
        return null;
    }
}
