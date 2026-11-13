package com.jjx.common.util;

import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 单位归一唯一工具（dev-20261113-003）。 */
class UnitCodesTest {

    @Test
    void normalizesKnownAliasesToDictCodes() {
        assertEquals("㎡", UnitCodes.normalize("㎡"));
        assertEquals("㎡", UnitCodes.normalize("m²"));
        assertEquals("㎡", UnitCodes.normalize("m2"));
        assertEquals("㎡", UnitCodes.normalize("M2"));
        assertEquals("㎡", UnitCodes.normalize("平方米"));
        assertEquals("㎡", UnitCodes.normalize("平方"));
        assertEquals("M", UnitCodes.normalize("米"));
        assertEquals("M", UnitCodes.normalize("m"));
        assertEquals("KG", UnitCodes.normalize("公斤"));
        assertEquals("PCS", UnitCodes.normalize("件"));
        assertEquals("ROLL", UnitCodes.normalize("卷"));
        assertEquals("SHEET", UnitCodes.normalize("张"));
        assertEquals("PIN", UnitCodes.normalize("针"));
    }

    @Test
    void unknownValueIsReturnedAsIsAndRejectedByWriterCheck() {
        assertEquals("支", UnitCodes.normalize("支"));
        assertFalse(UnitCodes.isDictUnit("支"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> UnitCodes.requireDictUnit("支", "物料单位"));
        assertTrue(ex.getMessage().contains("不是有效单位"));
        assertEquals("㎡", UnitCodes.requireDictUnit("M2", "物料单位"));
    }

    @Test
    void dictUnitsMatchDictionary() {
        assertEquals(Set.of("M", "㎡", "PCS", "KG", "ROLL", "SHEET", "SET", "PIN"), UnitCodes.dictUnits());
    }
}
