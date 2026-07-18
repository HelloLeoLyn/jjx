package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 付款状态枚举
 */
@Getter
public enum PaymentStatusEnum {

    /**
     * 待付款
     */
    PENDING(0, "待付款"),

    /**
     * 部分付款
     */
    PARTIALLY_PAID(1, "部分付款"),

    /**
     * 已付款
     */
    COMPLETED(2, "已付款");

    private final Integer code;
    private final String description;

    PaymentStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static PaymentStatusEnum getByCode(Integer code) {
        if (code == null) return null;
        for (PaymentStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据code获取描述
     */
    public static String getDescriptionByCode(Integer code) {
        PaymentStatusEnum status = getByCode(code);
        return status != null ? status.getDescription() : null;
    }
}
