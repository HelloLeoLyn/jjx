package com.jjx.inventory;

import com.jjx.inventory.master.MaterialSizeValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialSizeValueTest {
    @Test void rollDisplayConvertsOnlyRollAndKeepsMissingPositions() {
        var w = new java.math.BigDecimal("315");
        var l = new java.math.BigDecimal("1000000");
        var t = new java.math.BigDecimal("0.254");
        assertEquals("254μm*315mm*1000m", MaterialSizeValue.renderDisplay(w, l, t, "mm", "ROLL"));
        assertEquals("254μm", MaterialSizeValue.renderDisplay(null, null, t, null, "ROLL"));
        assertEquals("315mm", MaterialSizeValue.renderDisplay(w, null, null, "cm", "ROLL"));
        assertEquals("254μm*315mm", MaterialSizeValue.renderDisplay(w, null, t, "m", "ROLL"));
        assertEquals("254μm*1000m", MaterialSizeValue.renderDisplay(null, l, t, "mm", "ROLL"));
        assertNull(MaterialSizeValue.renderDisplay(null, null, null, "mm", "ROLL"));
        assertEquals("254.1μm", MaterialSizeValue.displaySegment(new java.math.BigDecimal("0.2541"), "thickness_mm", "ROLL", "mm").text());
        assertEquals("1.01m", MaterialSizeValue.displaySegment(new java.math.BigDecimal("1005"), "length", "ROLL", "mm").text());
        for (String unit : new String[]{"M", "PCS", "PIN", null}) {
            for (String sizeUnit : new String[]{"mm", "cm", "m", "", null}) {
                assertEquals(MaterialSizeValue.render(w, l, t, sizeUnit), MaterialSizeValue.renderDisplay(w, l, t, sizeUnit, unit));
                assertEquals(MaterialSizeValue.render(w, null, t, sizeUnit), MaterialSizeValue.renderDisplay(w, null, t, sizeUnit, unit));
            }
        }
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(w, l, t, "μm"));
    }
    @Test void unifiedSizeKeepsUnitsPositionsAndLegacyValues() {
        assertEquals("300*305*0.175mm", MaterialSizeValue.mergeLegacy("300×305mm", "0.175"));
        assertEquals("0.175", MaterialSizeValue.thickness("914**0.175mm"));
        assertEquals("0.05", MaterialSizeValue.thickness("Φ8*0.05mm"));
        assertNull(MaterialSizeValue.thickness("30*90mm"));
        assertEquals("按图纸", MaterialSizeValue.mergeLegacy("按图纸", 0.175));
        assertEquals("300", MaterialSizeValue.mergeLegacy("300", "280g"));
        assertEquals("**0.175mm", MaterialSizeValue.mergeLegacy("", 0.175));
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate("30*90mmmm"));
    }

    @Test void normalizeWritesHalfWidthAsteriskAndKeepsUnparsableText() {
        assertEquals("300*305", MaterialSizeValue.normalize("300×305"));
        assertEquals("300*305", MaterialSizeValue.normalize("300x305"));
        assertEquals("300*305", MaterialSizeValue.normalize("300X305"));
        assertEquals("300*305", MaterialSizeValue.normalize("300＊305"));
        assertEquals("300*305", MaterialSizeValue.normalize(" 300 * 305 "));
        assertEquals("Φ8*0.05mm", MaterialSizeValue.normalize("Φ8×0.05mm"));
        assertEquals("914**0.175mm", MaterialSizeValue.normalize("914××0.175mm"));
        assertEquals("300*305MM", MaterialSizeValue.normalize("300*305MM"));
        assertEquals("按图纸", MaterialSizeValue.normalize("按图纸"));
        assertEquals("30*90mmmm", MaterialSizeValue.normalize("30*90mmmm"));
        assertEquals(null, MaterialSizeValue.normalize(null));
    }
    @Test void numericDimensionsValidateStoragePrecisionAndRenderMissingPositions() {
        var w = new java.math.BigDecimal("300.00");
        var t = new java.math.BigDecimal("0.1750");
        assertEquals("300**0.175mm", MaterialSizeValue.render(w, null, t, "mm"));
        // 只填厚度合法：前导缺位不补占位（dev-20261030-016）
        assertEquals("0.175mm", MaterialSizeValue.render(null, null, t, "mm"));
        assertEquals("305*0.175mm", MaterialSizeValue.render(null, new java.math.BigDecimal("305"), t, "mm"));
        assertEquals("300cm", MaterialSizeValue.render(w, null, null, "cm"));
        assertNull(MaterialSizeValue.render(null, null, null, "mm"));
        assertEquals("cm", MaterialSizeValue.validate(w, null, null, " cm ").unit());
        for (String invalid : new String[]{"0", "-1", "1.001", "100000000"}) {
            assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(new java.math.BigDecimal(invalid), null, null, "mm"));
        }
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(w, null, t, "inch"));
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(w, null, new java.math.BigDecimal("0.00001"), "mm"));
    }
}
