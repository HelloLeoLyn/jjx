package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单类型枚举
 */
@Getter
@AllArgsConstructor
public enum OrderTypeEnum {
    STANDARD(1, "标准订单"),
    SAMPLE(2, "样品订单");

    private final Integer code;
    private final String desc;

    public static OrderTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}