package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出库类型枚举
 */
@Getter
@AllArgsConstructor
public enum OutboundTypeEnum {

    PRODUCTION("production", "生产领料"),
    SALES("sales", "销售出库"),
    RETURN("return", "退货出库"),
    SCRAP("scrap", "报废出库"),
    TRANSFER("transfer", "调拨出库"),
    ADJUST("adjust", "盘亏出库");

    private final String code;
    private final String label;

    public static OutboundTypeEnum getByCode(String code) {
        for (OutboundTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
