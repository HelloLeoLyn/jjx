package com.jjx.purchase.domain.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

/**
 * 付款状态枚举
 */
@Getter
public enum PaymentStatusEnum implements BizStatusEnum {

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

    private final Integer value;
    private final String label;

    PaymentStatusEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据code获取枚举
     */
    public static PaymentStatusEnum getByValue(Integer value) {
        if (value == null) return null;
        for (PaymentStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据code获取描述
     */
    public static String getLabelByValue(Integer value) {
        PaymentStatusEnum status = getByValue(value);
        return status != null ? status.getLabel() : null;
    }
}
