package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 预警类型枚举
 */
@Getter
@AllArgsConstructor
public enum AlertTypeEnum {

    SAFE_STOCK("safe_stock", "安全库存预警"),
    MAX_STOCK("max_stock", "最高库存预警"),
    EXPIRY("expiry", "保质期预警"),
    OBSOLETE("obsolete", "呆滞料预警"),
    ORDER_SHORTAGE("order_shortage", "订单缺料预警");

    private final String code;
    private final String label;

    public static AlertTypeEnum getByCode(String code) {
        for (AlertTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
