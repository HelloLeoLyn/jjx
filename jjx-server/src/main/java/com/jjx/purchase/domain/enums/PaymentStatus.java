package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 付款状态枚举
 */
@Getter
public enum PaymentStatus {

    /**
     * 待付款
     */
    PENDING("pending", "待付款"),

    /**
     * 部分付款
     */
    PARTIALLY_PAID("partially_paid", "部分付款"),

    /**
     * 已付款
     */
    PAID("paid", "已付款"),

    /**
     * 已完成
     */
    COMPLETED("completed", "已完成");

    private final String code;
    private final String description;

    PaymentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static PaymentStatus getByCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为待付款状态
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 判断是否为部分付款状态
     */
    public boolean isPartiallyPaid() {
        return this == PARTIALLY_PAID;
    }

    /**
     * 判断是否为已付款状态
     */
    public boolean isPaid() {
        return this == PAID || this == COMPLETED;
    }
}
