package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 询价状态枚举（用于采购订单明细）
 */
@Getter
public enum InquiryStatus {

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

    private final Integer code;
    private final String description;

    InquiryStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static InquiryStatus getByCode(Integer code) {
        for (InquiryStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
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
