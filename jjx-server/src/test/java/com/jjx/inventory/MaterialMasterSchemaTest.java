package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.jjx.inventory.master.MaterialMasterService;
import com.jjx.inventory.master.MaterialSourceValues;
import com.jjx.inventory.master.MaterialSizeValue;
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

    @Test void fieldUnitsApplyToEveryMaterialAndNamingKeepsMm() {
        var material = new InventoryMaterial(); material.setSizeUnit("cm");
        material.setWidthMm(new BigDecimal("315")); material.setLengthMm(new BigDecimal("1000000"));
        material.setThicknessMm(new BigDecimal("0.254"));
        var schema = MaterialAttributeSchema.parse("""
                {"version":1,"meta_options":[],"fields":[],"spec_pattern":{
                 "fields":[{"key":"thickness_mm","sep":"*","unit":"μm"},{"key":"width","sep":"*"},{"key":"length","sep":"*","unit":"m"}],
                 "with_unit":true,"unit_position":"each"}}
                """, mapper);
        var naming = new com.jjx.inventory.master.MaterialNamingPattern(List.of("thickness_mm", "width", "length"), "*", "skip");
        for (String unit : List.of("ROLL", "M", "PCS", "PIN")) {
            material.setUnit(unit);
            assertEquals("254μm*315mm*1000m", schema.specification(material, Map.of(), null));
            assertEquals("0.254*315*1000000", naming.compose(material, schema));
        }
        for (int mask = 0; mask < 8; mask++) {
            material.setThicknessMm((mask & 1) == 0 ? null : new BigDecimal("0.254"));
            material.setWidthMm((mask & 2) == 0 ? null : new BigDecimal("315"));
            material.setLengthMm((mask & 4) == 0 ? null : new BigDecimal("1000000"));
            List<String> expected = new ArrayList<>();
            if ((mask & 1) != 0) expected.add("254μm");
            if ((mask & 2) != 0) expected.add("315mm");
            if ((mask & 4) != 0) expected.add("1000m");
            assertEquals(expected.isEmpty() ? null : String.join("*", expected), schema.specification(material, Map.of(), null));
        }
    }

    @Test void unconfiguredTemplatesKeepExactOutputForAllMaterialUnits() throws Exception {
        var material = new InventoryMaterial(); material.setThicknessMm(new BigDecimal("0.175"));
        var fallback = seeds().get("TPL_PC");
        for (String unit : List.of("ROLL", "M", "PCS")) {
            material.setUnit(unit);
            material.setSizeUnit("m");
            material.setWidthMm(null);
            assertEquals("厚度:0.175mm；尺寸:0.175mm", fallback.specification(material, Map.of(), null));
            material.setWidthMm(new BigDecimal("300"));
            assertEquals("厚度:0.175mm；尺寸:300**0.175mm", fallback.specification(material, Map.of(), null));
        }
        material.setLengthMm(new BigDecimal("305"));
        for (String position : List.of("end", "each", "none")) {
            var schema = MaterialAttributeSchema.parse("""
                    {"version":1,"meta_options":[],"fields":[],"spec_pattern":{
                     "fields":[{"key":"width","sep":"*"},{"key":"length","sep":"*"},{"key":"thickness_mm","sep":"*"}],
                     "with_unit":true,"unit_position":"%s"}}
                    """.formatted(position), mapper);
            assertEquals(switch (position) {
                case "end" -> "300*305*0.175mm";
                case "each" -> "300mm*305mm*0.175mm";
                default -> "300*305*0.175";
            }, schema.specification(material, Map.of(), null));
        }
    }

    @Test void validatesDisplayConfigurationAndPreservesAttributeUnits() {
        String json = """
                {"version":1,"meta_options":[],"fields":[
                 {"key":"weight","label":"克重","type":"decimal","unit":"g/m²"},
                 {"key":"force","label":"离型力","type":"decimal","unit":"gf"}],"spec_pattern":{
                 "fields":[{"key":"thickness_mm","sep":"*","unit":"μm","scale":0},
                 {"key":"weight","sep":"*"},{"key":"force","sep":"*"}],"unit_position":"each"}}
                """;
        var material = new InventoryMaterial(); material.setThicknessMm(new BigDecimal("0.2541"));
        var schema = MaterialAttributeSchema.parse(json, mapper);
        assertEquals("254μm*80g/m²*25gf", schema.specification(material, Map.of("weight", 80, "force", 25), null));
        for (String invalid : List.of(json.replace("μm", "inch"), json.replace("\"scale\":0", "\"scale\":7"),
                json.replace("\"scale\":0", "\"scale\":-1"), json.replace("\"scale\":0", "\"scale\":1.5"),
                json.replace("\"scale\":0", "\"scale\":\"2\""), json.replace("\"key\":\"thickness_mm\"", "\"key\":\"weight\""))) {
            assertThrows(BusinessException.class, () -> MaterialAttributeSchema.parse(invalid, mapper));
        }
        assertThrows(BusinessException.class, () -> MaterialAttributeSchema.parse(json.replace("{\"key\":\"weight\",\"sep\":\"*\"}", "{\"key\":\"weight\",\"sep\":\"*\",\"scale\":2}"), mapper));
    }

    @Test void usedTemplatesAllowDimensionDisplayChangesButRejectAttributeUnitChanges() {
        String json = """
                {"version":1,"meta_options":[],"fields":[{"key":"force","label":"离型力","type":"decimal","unit":"gf"}],
                 "spec_pattern":{"fields":[{"key":"thickness_mm","sep":"*"}],"unit_position":"each"}}
                """;
        var old = MaterialAttributeSchema.parse(json, mapper);
        var next = MaterialAttributeSchema.parse(json.replace("\"key\":\"thickness_mm\"", "\"key\":\"thickness_mm\",\"unit\":\"μm\",\"scale\":0"), mapper);
        assertDoesNotThrow(() -> com.jjx.inventory.master.MaterialMasterConfigurationService.requireCompatibleUpdate(old, next, mapper));
        var invalid = MaterialAttributeSchema.parse(json.replace("gf", "N"), mapper);
        assertThrows(BusinessException.class, () -> com.jjx.inventory.master.MaterialMasterConfigurationService.requireCompatibleUpdate(old, invalid, mapper));
    }

    @Test void servicePreviewUsesSavedFieldUnitsWithoutChangingStoredSpecification() {
        var schema = MaterialAttributeSchema.parse("""
                {"version":1,"meta_options":[],"fields":[],"spec_pattern":{
                 "fields":[{"key":"thickness_mm","sep":"*","unit":"μm"},{"key":"width","sep":"*"},{"key":"length","sep":"*","unit":"m"}],
                 "unit_position":"each"}}
                """, mapper);
        var catalog = org.mockito.Mockito.mock(com.jjx.inventory.master.MaterialMasterCatalog.class);
        var materials = org.mockito.Mockito.mock(com.jjx.inventory.mapper.InventoryMaterialMapper.class);
        var service = new MaterialMasterService(catalog, null, materials, null, null, null, null, mapper);
        var category = new com.jjx.inventory.master.MaterialMasterCatalog.Category(1L, "TEST", "测试品类", null, 1, "R", "测试品类", true, 0, 1);
        org.mockito.Mockito.when(catalog.optionalConfiguration(1L)).thenReturn(new com.jjx.inventory.master.MaterialMasterCatalog.Configuration(category, List.of(category), null, schema));
        org.mockito.Mockito.when(catalog.specificationSchema(1L)).thenReturn(schema);
        var material = new InventoryMaterial(); material.setCategoryId(1L); material.setUnit("PCS");
        material.setWidthMm(new BigDecimal("315")); material.setLengthMm(new BigDecimal("1000000")); material.setThicknessMm(new BigDecimal("0.254"));
        material.setSpecification("旧规格快照");
        assertEquals("254μm*315mm*1000m", service.specificationPreview(material).specification());
        assertEquals("旧规格快照", material.getSpecification());
        var draft = new com.jjx.inventory.master.MaterialMasterDTO();
        draft.setWidthMm(material.getWidthMm()); draft.setLengthMm(material.getLengthMm()); draft.setThicknessMm(material.getThicknessMm()); draft.setSizeUnit("m");
        assertEquals("254μm*315mm*1000m", service.renderSpecification(1L, draft));
        org.mockito.Mockito.verifyNoInteractions(materials);
    }

    @Test void bomSpecificationSourceReturnsStoredSpecificationWithoutComposingAttributes() {
        var catalog = org.mockito.Mockito.mock(com.jjx.inventory.master.MaterialMasterCatalog.class);
        var service = new MaterialMasterService(catalog, null, null, null, null, null, null, mapper);
        var material = new InventoryMaterial();
        material.setSpecification("450*225mm");
        material.setAttributeJson("{\"coating\":\"单面加硬\"}");
        var source = service.specificationSource(material);
        assertEquals("450*225mm", source.get("specification"));
        assertEquals(Map.of("coating", "单面加硬"), source.get("attributes"));
        assertTrue(source.containsKey("size"));
        assertTrue(source.containsKey("schema"));
        material.setSpecification("");
        assertEquals("", service.specificationSource(material).get("specification"));
        assertNull(service.specificationSource(null).get("specification"));
    }

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

    @Test void dimensionsIncludeThicknessAndRequirePositiveValues() {
        for (String size : List.of("300", "300×305", "300*305", "300.5x400", "300×305×0.175", "300mm", "914**0.175mm", "Φ8*0.05mm")) assertDoesNotThrow(() -> MaterialMasterService.validateSize(size));
        for (String size : List.of("0×300", "-10", "0.3m×1.0", "300mmmm", "1*2*3*4", "***")) assertThrows(BusinessException.class, () -> MaterialMasterService.validateSize(size));
    }

    @Test void renderingMatchesPreviewWithVisibilityEmptyValuesAndDerivedDimensions() throws Exception {
        var schema = seeds().get("TPL_PC");
        assertEquals("尺寸:30*90mm", schema.compose(Map.of(), null, "30*90mm"));
        assertEquals("厚度:0.175mm；表面处理:中砂；抗UV:否；尺寸:300*305mm",
                schema.compose(Map.of("surface_treatment", List.of("MEDIUM_SAND"), "anti_uv", false), "0.175", "300×305"));
        assertEquals("尺寸:300*305mm", schema.compose(Map.of(), null, "300x305"));
        assertEquals("厚度:0.25mm", schema.compose(Map.of("thickness_mm", new BigDecimal("0.2500"),
                "surface_treatment", List.of(), "flame_rating", ""), "9", null));
        var electronics = seeds().get("TPL_ELECTRONIC");
        String pinLabel = electronics.fields().stream().filter(f -> f.key().equals("pin_count")).findFirst().orElseThrow().label();
        assertFalse(electronics.compose(Map.of("component_type", "LED", "pin_count", 4)).contains(pinLabel + ":"));
    }

    @Test void archivedSourcesStayEmptyAndRemarkIsUntouched() {
        var material = new InventoryMaterial(); material.setMaterialName("当前名称"); material.setSpecification("生成规格");
        String remark = "【原名称】旧名【原规格】300*305";
        material.setRemark(remark);
        assertEquals(new MaterialSourceValues(null, null), MaterialSourceValues.from(material));
        assertEquals(remark, material.getRemark());
    }
}
