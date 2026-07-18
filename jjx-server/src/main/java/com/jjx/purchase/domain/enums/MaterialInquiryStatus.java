package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 材料询价状态枚举（用于MaterialInquiry实体）
 */
@Getter
public enum MaterialInquiryStatus {

    /**
     * 有效
     */
    ACTIVE("active", "有效"),

    /**
     * 无效
     */
    INACTIVE("inactive", "无效"),

    /**
     * 已过期
     */
    EXPIRED("expired", "已过期");

    private final String code;
    private final String description;

    MaterialInquiryStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static MaterialInquiryStatus getByCode(String code) {
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
