package com.jjx.purchase.domain.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

/**
 * 收货状态枚举
 */
@Getter
public enum ReceiptStatusEnum implements BizStatusEnum {

    /**
     * 待收货
     */
    PENDING(0, "待收货"),

    /**
     * 部分收货
     */
    PARTIALLY_RECEIVED(1, "部分收货"),

    /**
     * 已收货
     */
    COMPLETED(2, "已收货");

    private final Integer value;
    private final String label;

    ReceiptStatusEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据code获取枚举
     */
    public static ReceiptStatusEnum getByValue(Integer value) {
        if (value == null) return null;
        for (ReceiptStatusEnum status : values()) {
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
        ReceiptStatusEnum status = getByValue(value);
        return status != null ? status.getLabel() : null;
    }
}
