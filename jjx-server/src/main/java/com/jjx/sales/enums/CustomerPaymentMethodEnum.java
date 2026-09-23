package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 客户付款方式（sales_customer.payment_method）。 */
@Getter
@AllArgsConstructor
public enum CustomerPaymentMethodEnum implements BizStatusEnum {
    PREPAID(1, "预付"),
    CASH_ON_DELIVERY(2, "货到付款"),
    MONTHLY_30(3, "月结30天"),
    MONTHLY_60(4, "月结60天");

    private final Integer value;
    private final String label;
}
