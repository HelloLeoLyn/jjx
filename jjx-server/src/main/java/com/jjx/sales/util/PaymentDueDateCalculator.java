package com.jjx.sales.util;

import com.jjx.sales.enums.PaymentTermTypeEnum;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** 按客户签收日计算应付到期日。 */
public final class PaymentDueDateCalculator {
    private PaymentDueDateCalculator() {
    }

    public static LocalDate calculate(PaymentTermTypeEnum type, Integer creditDays, LocalDate receiptDate) {
        if (type == null || receiptDate == null || type == PaymentTermTypeEnum.PREPAID) {
            return null;
        }
        int days = creditDays == null ? 0 : Math.max(creditDays, 0);
        return switch (type) {
            case COD -> receiptDate;
            case NET_DAYS -> receiptDate.plusDays(days);
            case MONTH_END -> receiptDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(days);
            case PREPAID -> null;
        };
    }
}
