package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.master.MaterialAttributeSchema;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MaterialAttributeSchemaSeparatorTest {
    private MaterialAttributeSchema schema(String separator) {
        return new MaterialAttributeSchema(1, List.of(), false, null, null, null, List.of(), null,
                new MaterialAttributeSchema.SpecPattern(List.of(
                        new MaterialAttributeSchema.SpecPattern.SpecField("brand", ""),
                        new MaterialAttributeSchema.SpecPattern.SpecField("model", separator)), false, false, "none"));
    }

    private InventoryMaterial material() {
        var material = new InventoryMaterial();
        material.setBrand("3M");
        material.setModel("125");
        return material;
    }

    @Test
    void 规格保存解析回读与生成保留多字符空格和紧贴() throws Exception {
        var mapper = new ObjectMapper();
        for (String sep : List.of("-", "/-", " / ", " ", "😀/😀", "")) {
            var read = MaterialAttributeSchema.parse(mapper.writeValueAsString(schema(sep)), mapper);
            assertEquals(sep, read.specPattern().fields().get(1).sep());
            assertEquals("3M" + sep + "125", read.specification(material(), Map.of(), null));
        }
        var read = MaterialAttributeSchema.parse(mapper.writeValueAsString(schema(null)), mapper);
        assertNull(read.specPattern().fields().get(1).sep());
        assertEquals("3M；125", read.specification(material(), Map.of(), null));
    }

    @Test
    void 规格定义与生成均拒绝超长和控制字符() {
        for (String sep : List.of("abcd", "\n", "\t", "\r", "\u0000", "\u007f", "\u0085")) {
            var schema = schema(sep);
            assertThrows(BusinessException.class, schema::validateDefinition);
            assertThrows(BusinessException.class, () -> schema.specification(material(), Map.of(), null));
        }
    }
}
