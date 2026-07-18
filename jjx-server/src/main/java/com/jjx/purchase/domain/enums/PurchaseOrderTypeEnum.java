package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 采购订单类型枚举
 */
@Getter
public enum PurchaseOrderTypeEnum {

    /**
     * 正常
     */
    NORMAL(0, "正常"),

    /**
     * 紧急
     */
    URGENT(1, "紧急");

    private final Integer code;
    private final String description;

    PurchaseOrderTypeEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static PurchaseOrderTypeEnum getByCode(Integer code) {
        if (code == null) return null;
        for (PurchaseOrderTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据code获取描述
     */
    public static String getDescriptionByCode(Integer code) {
        PurchaseOrderTypeEnum type = getByCode(code);
        return type != null ? type.getDescription() : null;
    }
}
