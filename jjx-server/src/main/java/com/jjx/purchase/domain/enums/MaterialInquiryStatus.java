package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 材料询价状态枚举（用于MaterialInquiry实体）
 * 数字编码: 0有效/1无效/2已过期/3已取消/4已完成
 */
@Getter
public enum MaterialInquiryStatus {

    /**
     * 有效
     */
    ACTIVE(0, "有效"),

    /**
     * 无效
     */
    INACTIVE(1, "无效"),

    /**
     * 已过期
     */
    EXPIRED(2, "已过期"),

    /**
     * 已取消
     */
    CANCELLED(3, "已取消"),

    /**
     * 已完成
     */
    COMPLETED(4, "已完成");

    private final Integer code;
    private final String description;

    MaterialInquiryStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static MaterialInquiryStatus getByCode(Integer code) {
        if (code == null) return null;
        for (MaterialInquiryStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为有效状态
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 判断是否为已过期状态
     */
    public boolean isExpired() {
        return this == EXPIRED;
    }
}
