package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 收货状态枚举
 */
@Getter
public enum ReceiptStatus {

    /**
     * 待收货
     */
    PENDING("pending", "待收货"),

    /**
     * 部分收货
     */
    PARTIALLY_RECEIVED("partially_received", "部分收货"),

    /**
     * 已收货
     */
    COMPLETED("completed", "已收货");

    private final String code;
    private final String description;

    ReceiptStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static ReceiptStatus getByCode(String code) {
        for (ReceiptStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为待收货状态
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 判断是否为部分收货状态
     */
    public boolean isPartiallyReceived() {
        return this == PARTIALLY_RECEIVED;
    }

    /**
     * 判断是否为已收货状态
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 判断是否可以收货
     */
    public boolean canReceive() {
        return this == PENDING || this == PARTIALLY_RECEIVED;
    }
}
