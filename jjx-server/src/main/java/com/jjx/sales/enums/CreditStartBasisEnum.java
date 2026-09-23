package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 客户账期起算基准。 */
@Getter
@AllArgsConstructor
public enum CreditStartBasisEnum {
    CUSTOMER_RECEIPT_DATE("客户签收日");

    private final String label;
}
