package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付状态枚举
 */
@Getter
@AllArgsConstructor
public enum PaymentStatusEnum {
    UNPAID(1, "未支付"),
    PAYING(2, "支付中"),
    PAID(3, "已支付"),
    PARTIAL_PAID(4, "部分支付"),
    REFUNDED(5, "已退款");

    private final Integer code;
    private final String desc;

    public static PaymentStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}