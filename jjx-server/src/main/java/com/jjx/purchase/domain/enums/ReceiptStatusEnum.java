package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 收货状态枚举
 */
@Getter
public enum ReceiptStatusEnum {

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

    private final Integer code;
    private final String description;

    ReceiptStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static ReceiptStatusEnum getByCode(Integer code) {
        if (code == null) return null;
        for (ReceiptStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据code获取描述
     */
    public static String getDescriptionByCode(Integer code) {
        ReceiptStatusEnum status = getByCode(code);
        return status != null ? status.getDescription() : null;
    }
}
