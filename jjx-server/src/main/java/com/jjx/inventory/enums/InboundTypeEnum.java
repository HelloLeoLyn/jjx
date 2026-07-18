package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 入库类型枚举
 */
@Getter
@AllArgsConstructor
public enum InboundTypeEnum {

    PURCHASE("purchase", "采购入库"),
    PRODUCTION("production", "生产入库"),
    RETURN("return", "退货入库"),
    TRANSFER("transfer", "调拨入库"),
    ADJUST("adjust", "盘盈入库");

    private final String code;
    private final String label;

    public static InboundTypeEnum getByCode(String code) {
        for (InboundTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
