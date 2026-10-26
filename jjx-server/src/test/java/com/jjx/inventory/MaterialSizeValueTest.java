package com.jjx.inventory;

import com.jjx.inventory.master.MaterialSizeValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialSizeValueTest {
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
}
