package com.jjx.purchase.domain.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

/**
 * 询价状态枚举（用于采购订单明细）
 */
@Getter
public enum InquiryStatus implements BizStatusEnum {

    /**
     * 待询价
     */
    PENDING(0, "待询价"),

    /**
     * 已询价
     */
    INQUIRED(1, "已询价"),

    /**
     * 比价中
     */
    COMPARING(2, "比价中"),

    /**
     * 已选中
     */
    SELECTED(3, "已选中");

    private final Integer value;
    private final String label;

    InquiryStatus(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据code获取枚举
     */
    public static InquiryStatus getByValue(Integer value) {
        for (InquiryStatus status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }

    public String getCodeString() {
        return value+"";
    }

    /**
     * 判断是否为待询价状态
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 判断是否为已选中状态
     */
    public boolean isSelected() {
        return this == SELECTED;
    }
}
