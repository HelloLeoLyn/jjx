package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.jjx.inventory.master.MaterialNamingPattern;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MaterialRuleAffixTest {
    private final ObjectMapper json = new ObjectMapper();
    private final InventoryMaterial material = new InventoryMaterial();

    @Test void nameAndModelAffixesRoundTripAndMissingFieldsDoNotEmitAffixes() throws Exception {
        material.setColor("黑色");
        var pattern = json.readValue("""
            {"fields":["brand","color"],"separator":"-","empty":"skip",
             "prefixes":{"brand":"品牌[","color":"颜色["},"suffixes":{"brand":"]","color":"]"}}
            """, MaterialNamingPattern.class);
        var read = json.readValue(json.writeValueAsString(pattern.normalized()), MaterialNamingPattern.class);
        assertEquals("颜色[黑色]", read.compose(material, null));
        assertEquals("颜色[黑色]", read.forModel().compose(material, null));
        material.setMaterialName("颜色[黑色]");
        assertEquals("颜色[黑色]", read.compose(material, null), "重新生成不能在旧名称上叠加前后缀");
    }

    @Test void placeholderDoesNotGetDecoratedAndLegacyRulesKeepOutput() throws Exception {
        material.setModel("A1");
        var pattern = json.readValue("""
            {"fields":["brand","model"],"separator":"-","empty":"placeholder","prefixes":{"brand":"品牌"}}
            """, MaterialNamingPattern.class);
        assertEquals("—-A1", pattern.compose(material, null));
        assertEquals("A1", new MaterialNamingPattern(List.of("model"), "-", "skip").compose(material, null));
    }

    private MaterialAttributeSchema spec(String position, String suffix) throws Exception {
        return MaterialAttributeSchema.parse("""
            {"version":1,"meta_options":[],"fields":[],"spec_pattern":{
              "fields":[{"key":"thickness_mm","sep":"","prefix":"厚","suffix":"%s"},
                        {"key":"length","sep":"*","prefix":"长"},
                        {"key":"width","sep":"*","prefix":"宽","suffix":"%s"}],
              "with_unit":true,"unit_position":"%s"}}
            """.formatted(suffix, suffix, position), json);
    }

    @Test void specificationSkipsMissingDimensionsAndAvoidsDuplicateUnits() throws Exception {
        material.setWidthMm(new BigDecimal("365")); material.setThicknessMm(new BigDecimal("2.0"));
        assertEquals("厚2mm*宽365mm", spec("each", "mm").specification(material, Map.of(), null));
        assertEquals("厚2mm*宽365mm", spec("end", "mm").specification(material, Map.of(), null));
        assertEquals("厚2mm*宽365mm", spec("each", "").specification(material, Map.of(), null));
        assertEquals("厚2*宽365mm", spec("end", "").specification(material, Map.of(), null));
        material.setWidthMm(null); material.setThicknessMm(null);
        assertNull(spec("each", "mm").specification(material, Map.of(), null));
    }

    @Test void affixLengthsAndControlsAreValidatedAtDefinitionAndRender() {
        for (String value : List.of("x".repeat(51), "\n", "\t", "\u0000")) {
            assertThrows(BusinessException.class, () -> MaterialNamingPattern.validatedAffix(value));
            var rule = new MaterialNamingPattern(List.of("model"), "-", "skip", Map.of(), Map.of(), Map.of("model", value), Map.of());
            assertThrows(BusinessException.class, rule::normalized);
        }
        assertEquals(" 中文 / ", MaterialNamingPattern.validatedAffix(" 中文 / "));
    }
}
