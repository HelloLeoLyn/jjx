package com.jjx.purchase.domain.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

/**
 * 票据状态枚举
 */
@Getter
public enum DocumentStatus implements BizStatusEnum {

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

    private final Integer value;
    private final String label;

    DocumentStatus(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据code获取枚举
     */
    public static DocumentStatus getByValue(Integer value) {
        for (DocumentStatus status : values()) {
            if (status.getValue().equals(value)) {
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
