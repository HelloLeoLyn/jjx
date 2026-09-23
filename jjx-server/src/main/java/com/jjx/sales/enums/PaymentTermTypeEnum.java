package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 客户账期类型。 */
@Getter
@AllArgsConstructor
public enum PaymentTermTypeEnum {
    PREPAID("预付"),
    COD("货到付款"),
    NET_DAYS("签收后N天"),
    MONTH_END("签收月月底后N天");

    private final String label;

    public static PaymentTermTypeEnum parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
