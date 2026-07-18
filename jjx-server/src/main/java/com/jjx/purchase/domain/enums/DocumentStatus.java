package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 票据状态枚举
 */
@Getter
public enum DocumentStatus {

    /**
     * 待处理
     */
    PENDING("pending", "待处理"),

    /**
     * 已核验
     */
    VERIFIED("verified", "已核验"),

    /**
     * 已归档
     */
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String description;

    DocumentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static DocumentStatus getByCode(String code) {
        for (DocumentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为待处理状态
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 判断是否为已核验状态
     */
    public boolean isVerified() {
        return this == VERIFIED;
    }
}
