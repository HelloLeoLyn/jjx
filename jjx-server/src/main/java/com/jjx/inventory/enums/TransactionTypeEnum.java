package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易类型枚举（库存流水）
 */
@Getter
@AllArgsConstructor
public enum TransactionTypeEnum {

    INBOUND("inbound", "入库"),
    OUTBOUND("outbound", "出库"),
    TRANSFER_IN("transfer_in", "调拨入库"),
    TRANSFER_OUT("transfer_out", "调拨出库"),
    ADJUST("adjust", "盘盈盘亏");

    private final String code;
    private final String label;

    public static TransactionTypeEnum getByCode(String code) {
        for (TransactionTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
