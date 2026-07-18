package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存明细状态枚举
 * 对应 inventory_stock_item.status 字段
 */
@Getter
@AllArgsConstructor
public enum StockItemStatusEnum {

    INACTIVE(0, "未生效"),
    ACTIVE(1, "生效");

    private final Integer code;
    private final String label;

    public static StockItemStatusEnum getByCode(Integer code) {
        if (code == null) return null;
        for (StockItemStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
