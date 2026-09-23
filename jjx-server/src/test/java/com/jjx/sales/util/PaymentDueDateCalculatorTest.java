package com.jjx.sales.util;

import com.jjx.sales.enums.PaymentTermTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentDueDateCalculatorTest {

    private static final LocalDate RECEIPT_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void shouldCalculateFromCustomerReceiptDate() {
        assertNull(PaymentDueDateCalculator.calculate(PaymentTermTypeEnum.PREPAID, 0, RECEIPT_DATE));
        assertEquals(RECEIPT_DATE,
                PaymentDueDateCalculator.calculate(PaymentTermTypeEnum.COD, 0, RECEIPT_DATE));
        assertEquals(LocalDate.of(2026, 10, 23),
                PaymentDueDateCalculator.calculate(PaymentTermTypeEnum.NET_DAYS, 30, RECEIPT_DATE));
        assertEquals(LocalDate.of(2026, 10, 30),
                PaymentDueDateCalculator.calculate(PaymentTermTypeEnum.MONTH_END, 30, RECEIPT_DATE));
    }
}
