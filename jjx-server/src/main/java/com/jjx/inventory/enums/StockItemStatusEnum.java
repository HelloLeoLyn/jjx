package com.jjx.inventory.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存明细状态枚举
 * 对应 inventory_stock_item.status 字段
 */
@Getter
@AllArgsConstructor
public enum StockItemStatusEnum implements BizStatusEnum {

    INACTIVE(0, "未生效"),
    ACTIVE(1, "生效");

    private final Integer value;
    private final String label;

    public static StockItemStatusEnum getByValue(Integer value) {
        if (value == null) return null;
        for (StockItemStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }

}
