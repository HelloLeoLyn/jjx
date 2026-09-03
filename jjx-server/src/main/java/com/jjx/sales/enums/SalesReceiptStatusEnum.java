package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 销售收款单状态枚举（sales_receipt.status）
 */
@Getter
@AllArgsConstructor
public enum SalesReceiptStatusEnum implements BizStatusEnum {
    NORMAL(1, "正常"),
    VOID(0, "作废");

    private final Integer value;
    private final String label;

    public static SalesReceiptStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SalesReceiptStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
