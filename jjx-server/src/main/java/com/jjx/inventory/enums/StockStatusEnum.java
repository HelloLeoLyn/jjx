package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存状态枚举
 */
@Getter
@AllArgsConstructor
public enum StockStatusEnum {

    ACTIVE("active", "正常"),
    FROZEN("frozen", "冻结"),
    EXPIRED("expired", "过期"),
    SCRAP("scrap", "报废");

    private final String code;
    private final String label;

    public static StockStatusEnum getByCode(String code) {
        for (StockStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
