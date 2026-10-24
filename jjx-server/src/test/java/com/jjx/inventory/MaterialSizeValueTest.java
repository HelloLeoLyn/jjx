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
}
