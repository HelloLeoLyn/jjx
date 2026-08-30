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
    private final String label;

    PurchaseOrderTypeEnum(Integer code, String description) {
        this.code = code;
        this.label = description;
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

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getDescription() {
        return label;
    }
}
