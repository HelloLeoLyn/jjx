package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 订单类型枚举
 */
@Getter
public enum OrderType {

    /**
     * 正常
     */
    NORMAL("normal", "正常"),

    /**
     * 紧急
     */
    URGENT("urgent", "紧急");

    private final String code;
    private final String label;

    OrderType(String code, String description) {
        this.code = code;
        this.label = description;
    }

    /**
     * 根据code获取枚举
     */
    public static OrderType getByCode(String code) {
        for (OrderType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断是否为紧急订单
     */
    public boolean isUrgent() {
        return this == URGENT;
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getDescription() {
        return label;
    }
}
