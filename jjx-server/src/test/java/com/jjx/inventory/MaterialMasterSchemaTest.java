package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.jjx.inventory.master.MaterialMasterService;
import com.jjx.inventory.master.MaterialSourceValues;
import com.jjx.inventory.domain.InventoryMaterial;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class MaterialMasterSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, MaterialAttributeSchema> seeds() throws Exception {
        Path path = Path.of("../jjx-docs/sql/migrations/304_material_master_catalog.sql");
        if (!Files.exists(path)) path = Path.of("../jjx-docs/sql/migrations/applied/304_material_master_catalog.sql");
        String sql = Files.readString(path);
        var matcher = Pattern.compile("SELECT '(TPL_[A-Z_]+)','[^']*',c.category_id,'(.*?)',1,'Codex'", Pattern.DOTALL).matcher(sql);
        Map<String, MaterialAttributeSchema> result = new LinkedHashMap<>();
        while (matcher.find()) result.put(matcher.group(1), MaterialAttributeSchema.parse(matcher.group(2).replace("''", "'").replace("\\\\", "\\"), mapper));
        return result;
    }

    @Test void allSeedTemplatesAreValidAndTypesRemainSeparate() throws Exception {
        var schemas = seeds();
        assertEquals(16, schemas.size());
        assertTrue(schemas.containsKey("TPL_AUX_OTHER"));
        assertFalse(schemas.get("TPL_TAPE").metaOptions().contains("3M"));
        assertFalse(schemas.get("TPL_TAPE").metaOptions().contains("TESA"));
        assertTrue(schemas.get("TPL_ELECTRONIC").metaOptions().isEmpty());
        assertTrue(schemas.get("TPL_SEMI").fields().stream().noneMatch(f -> f.key().equals("process_status")));
    }

    @Test void validatesValuesWithoutGuessingUnknownFacts() throws Exception {
        var schema = seeds().get("TPL_PC");
        var values = schema.validateAttributes(Map.of("thickness_mm", new BigDecimal("0.175"),
                "surface_treatment", List.of("MEDIUM_SAND", "HARD_COATED"), "anti_uv", false), mapper);
        assertEquals(false, values.get("anti_uv"));
        assertTrue(schema.compose(values).contains("中砂、加硬"));
        assertTrue(schema.compose(values).contains("0.175mm"));
        assertTrue(schema.validateAttributes(Map.of(), mapper).isEmpty());
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("unknown", 1), mapper));
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("anti_uv", "false"), mapper));
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("thickness_mm", "0.175mm"), mapper));
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("thickness_mm", -1), mapper));
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("surface_treatment", List.of("MEDIUM_SAND", "MEDIUM_SAND")), mapper));
    }

    @Test void conditionalAttributesRejectStaleCategoryValuesAndInvalidNumbers() throws Exception {
        var schema = seeds().get("TPL_ELECTRONIC");
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of(), mapper));
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("component_type", "LED", "pin_count", 4), mapper));
        assertThrows(BusinessException.class, () -> schema.validateAttributes(Map.of("component_type", "CONNECTOR", "pin_count", 4.5), mapper));
        var values = schema.validateAttributes(Map.of("component_type", "CONNECTOR", "pin_count", 4, "pitch_mm", 2.54), mapper);
        assertEquals(4, values.get("pin_count"));
        var optical = seeds().get("TPL_OPTICAL");
        assertThrows(BusinessException.class, () -> optical.validateAttributes(Map.of("optical_type", "DIFFUSER", "haze_pct", 101), mapper));
    }

    @Test void dimensionsSeparateThicknessAndRequirePositiveValues() {
        for (String size : List.of("300", "300×305", "300*305", "300.5x400")) assertDoesNotThrow(() -> MaterialMasterService.validateSize(size));
        for (String size : List.of("300×305×0.175", "0×300", "-10", "0.3m×1.0", "300mm")) assertThrows(BusinessException.class, () -> MaterialMasterService.validateSize(size));
    }

    @Test void renderingMatchesPreviewWithVisibilityEmptyValuesAndDerivedDimensions() throws Exception {
        var schema = seeds().get("TPL_PC");
        assertEquals("厚度:0.175mm；表面处理:中砂；抗UV:否；尺寸:300×305mm",
                schema.compose(Map.of("surface_treatment", List.of("MEDIUM_SAND"), "anti_uv", false), "0.175", "300×305"));
        assertEquals("厚度:0.25mm", schema.compose(Map.of("thickness_mm", new BigDecimal("0.2500"),
                "surface_treatment", List.of(), "flame_rating", ""), "9", null));
        var electronics = seeds().get("TPL_ELECTRONIC");
        String pinLabel = electronics.fields().stream().filter(f -> f.key().equals("pin_count")).findFirst().orElseThrow().label();
        assertFalse(electronics.compose(Map.of("component_type", "LED", "pin_count", 4)).contains(pinLabel + ":"));
    }

    @Test void sourcesPreferExplicitOriginalMarkersAndNeverBackfillGeneratedTextIntoOriginalSpec() {
        var material = new InventoryMaterial(); material.setMaterialName("拼接后的名称"); material.setSpecification("生成规格");
        material.setRemark("原供应商:地博(未匹配)【原名称】0.175中砂面PC-DB6842【原规格】300*305");
        assertEquals(new MaterialSourceValues("0.175中砂面PC-DB6842","300*305"),MaterialSourceValues.from(material));
        material.setOriginalName("已确认来源"); material.setOriginalSpecification(null);
        assertEquals(new MaterialSourceValues("已确认来源",null),MaterialSourceValues.from(material));
    }
}
