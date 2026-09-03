package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 销售发票状态枚举（sales_invoice.invoice_status）
 */
@Getter
@AllArgsConstructor
public enum SalesInvoiceStatusEnum implements BizStatusEnum {
    NORMAL(1, "正常"),
    CANCELLED(0, "作废");

    private final Integer value;
    private final String label;

    public static SalesInvoiceStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SalesInvoiceStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
