package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 付款方式枚举
 */
@Getter
public enum PaymentMethod {

    /**
     * 银行转账
     */
    BANK("bank", "银行转账"),

    /**
     * 现金
     */
    CASH("cash", "现金"),

    /**
     * 支票
     */
    CHECK("check", "支票");

    private final String code;
    private final String label;

    PaymentMethod(String code, String description) {
        this.code = code;
        this.label = description;
    }

    /**
     * 根据code获取枚举
     */
    public static PaymentMethod getByCode(String code) {
        for (PaymentMethod method : values()) {
            if (method.getCode().equals(code)) {
                return method;
            }
        }
        return null;
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getDescription() {
        return label;
    }
}
