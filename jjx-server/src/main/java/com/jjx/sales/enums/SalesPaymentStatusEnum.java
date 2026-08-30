package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付状态枚举
 */
@Getter
@AllArgsConstructor
public enum SalesPaymentStatusEnum implements BizStatusEnum {
    UNPAID(1, "未支付"),
    PAYING(2, "支付中"),
    PAID(3, "已支付"),
    PARTIAL_PAID(4, "部分支付"),
    REFUNDED(5, "已退款");

    private final Integer value;
    private final String label;

    public static SalesPaymentStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SalesPaymentStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}