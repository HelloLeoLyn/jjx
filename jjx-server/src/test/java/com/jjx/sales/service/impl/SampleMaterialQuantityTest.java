package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 打样材料数量口径：模数/基数至少为1的整数，单位用量=基数÷模数（四位）；
 * 历史小数用量约分为整数基数/模数（dev-20261015-017）。
 */
class SampleMaterialQuantityTest {

    private static void assertValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void derivesIntegerRatioFromLegacyDecimal() {
        BigDecimal[] quarter = SampleMaterialQuantity.normalize(null, null, new BigDecimal("0.25"));
        assertValue("1", quarter[0]);
        assertValue("4", quarter[1]);
        assertValue("0.2500", quarter[2]);

        BigDecimal[] half = SampleMaterialQuantity.normalize(null, null, new BigDecimal("0.5"));
        assertValue("1", half[0]);
        assertValue("2", half[1]);
    }

    @Test
    void keepsIntegerLegacyQuantity() {
        BigDecimal[] whole = SampleMaterialQuantity.normalize(null, null, new BigDecimal("3"));
        assertValue("3", whole[0]);
        assertValue("1", whole[1]);
        assertValue("3.0000", whole[2]);
    }

    @Test
    void explicitBaseAndModuleWinOverLegacy() {
        BigDecimal[] fromBaseModule = SampleMaterialQuantity.normalize(new BigDecimal("3"), new BigDecimal("4"), new BigDecimal("9"));
        assertValue("3", fromBaseModule[0]);
        assertValue("4", fromBaseModule[1]);
        assertValue("0.7500", fromBaseModule[2]);
    }

    @Test
    void rejectsInvalidBaseModule() {
        assertThrows(BusinessException.class, () -> SampleMaterialQuantity.normalize(BigDecimal.ZERO, BigDecimal.ONE, null));
        assertThrows(BusinessException.class, () -> SampleMaterialQuantity.normalize(BigDecimal.ONE, BigDecimal.ZERO, null));
        assertThrows(BusinessException.class, () -> SampleMaterialQuantity.normalize(new BigDecimal("1.5"), BigDecimal.ONE, null));
    }

    @Test
    void rejectsInvalidLegacyQuantity() {
        assertThrows(BusinessException.class, () -> SampleMaterialQuantity.normalize(null, null, BigDecimal.ZERO));
        assertThrows(BusinessException.class, () -> SampleMaterialQuantity.normalize(null, null, new BigDecimal("-1")));
        assertThrows(BusinessException.class, () -> SampleMaterialQuantity.normalize(null, null, new BigDecimal("0.12345")));
    }
}
