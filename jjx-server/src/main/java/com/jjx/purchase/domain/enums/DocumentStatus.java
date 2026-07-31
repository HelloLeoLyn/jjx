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
    PENDING(0, "待处理"),

    /**
     * 已核验
     */
    VERIFIED(1, "已核验"),

    /**
     * 已归档
     */
    ARCHIVED(2, "已归档");

    private final Integer code;
    private final String description;

    DocumentStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static DocumentStatus getByCode(Integer code) {
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
