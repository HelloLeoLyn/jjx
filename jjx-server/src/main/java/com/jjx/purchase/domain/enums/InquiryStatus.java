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
    PENDING("pending", "待询价"),

    /**
     * 已询价
     */
    INQUIRED("inquired", "已询价"),

    /**
     * 比价中
     */
    COMPARING("comparing", "比价中"),

    /**
     * 已选中
     */
    SELECTED("selected", "已选中");

    private final String code;
    private final String description;

    InquiryStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static InquiryStatus getByCode(String code) {
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
