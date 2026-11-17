package com.jjx.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.jjx.inventory.master.MaterialMasterConfigurationService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MaterialTemplateCompatibilityTest {
    final ObjectMapper mapper = new ObjectMapper();
    MaterialAttributeSchema schema(String fields) {
        return MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[],\"default_unit\":\"PCS\",\"fields\":[" + fields + "]}", mapper);
    }
    final String oldField = "{\"key\":\"force_g\",\"label\":\"力值\",\"type\":\"decimal\",\"unit\":\"gf\",\"required\":false}";

    @Test void optionalAttributeAndDisplaySettingsAreCompatibleAndDoNotInventOldValues() {
        var old = schema(oldField);
        var next = schema(oldField.replace("力值", "操作力").replace("\"required\":false", "\"required\":false,\"common\":true,\"in_spec\":true,\"spec_order\":10")
                + ",{\"key\":\"has_dimple\",\"label\":\"带点\",\"type\":\"boolean\",\"required\":false,\"true_label\":\"带点\",\"false_label\":\"无点\"}");
        assertDoesNotThrow(() -> MaterialMasterConfigurationService.requireCompatibleUpdate(old, next, mapper));
        assertEquals(Map.of("force_g", 280), next.validateAttributes(Map.of("force_g", 280), mapper));
        assertFalse(next.validateAttributes(Map.of("force_g", 280), mapper).containsKey("has_dimple"));
    }

    @Test void destructiveChangesRequireReferenceChecks() {
        var old = schema(oldField);
        for (String candidate : new String[]{"", oldField.replace("gf", "g"), oldField.replace("decimal", "string"),
                oldField + ",{\"key\":\"new_field\",\"label\":\"新项\",\"type\":\"string\",\"required\":true}"}) {
            assertThrows(RuntimeException.class, () -> MaterialMasterConfigurationService.requireCompatibleUpdate(old, schema(candidate), mapper));
        }
    }

    @Test void defaultsAndAdditionalOptionsAreAllowedWithoutInventingOldValues() {
        var old = schema("{\"key\":\"glue\",\"label\":\"胶系\",\"type\":\"enum\",\"options\":[{\"code\":\"A\",\"label\":\"甲\"}]}");
        var next = MaterialAttributeSchema.parse(mapper.valueToTree(old).toString()
                .replace("\"default_unit\":\"PCS\"", "\"default_unit\":\"M\"")
                .replace("\"label\":\"甲\"", "\"label\":\"甲改名\"},{\"code\":\"B\",\"label\":\"乙\"")
                .replace("\"default\":null", "\"default\":\"B\""), mapper);
        assertDoesNotThrow(() -> MaterialMasterConfigurationService.requireCompatibleUpdate(old, next, mapper));
        assertEquals(Map.of("glue", "B"), next.validateAttributes(Map.of(), mapper));
        assertEquals(Map.of(), next.validateAttributes(Map.of(), mapper, false));
        assertEquals(Map.of("glue", "A"), next.validateAttributes(Map.of("glue", "A"), mapper, false));
    }

    @Test void referencedOptionRemovalReportsMaterialButUnusedRemovalAndLabelChangesPass() {
        var old = schema("{\"key\":\"glue\",\"label\":\"胶系\",\"type\":\"multienum\",\"options\":[{\"code\":\"A\",\"label\":\"甲\"},{\"code\":\"B\",\"label\":\"乙\"}]}");
        var next = schema("{\"key\":\"glue\",\"label\":\"胶系\",\"type\":\"multienum\",\"options\":[{\"code\":\"B\",\"label\":\"乙改名\"}]}");
        var material = new com.jjx.inventory.domain.InventoryMaterial();
        material.setMaterialCode("RM000352"); material.setAttributeJson("{\"glue\":[\"A\"]}");
        var ex = assertThrows(RuntimeException.class, () -> MaterialMasterConfigurationService.requireMaterialCompatibility(old, next, material, mapper));
        assertTrue(ex.getMessage().contains("RM000352")); assertTrue(ex.getMessage().contains("胶系"));
        material.setAttributeJson("{\"glue\":[\"B\"]}");
        assertDoesNotThrow(() -> MaterialMasterConfigurationService.requireMaterialCompatibility(old, next, material, mapper));
        assertEquals("{\"glue\":[\"B\"]}", material.getAttributeJson());
    }

    @Test void usedUnitsAndRemovedMaterialsAreProtectedButUnusedFieldsCanChange() {
        var old = schema(oldField); var next = schema(oldField.replace("gf", "g"));
        var material = new com.jjx.inventory.domain.InventoryMaterial(); material.setMaterialCode("RM1");
        material.setAttributeJson("{\"force_g\":0}");
        assertThrows(RuntimeException.class, () -> MaterialMasterConfigurationService.requireMaterialCompatibility(old, next, material, mapper));
        material.setAttributeJson("{}");
        assertDoesNotThrow(() -> MaterialMasterConfigurationService.requireMaterialCompatibility(old, next, material, mapper));
        var metaOld = MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[\"PC\",\"PET\"],\"fields\":[]}", mapper);
        var metaNew = MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[\"PC\"],\"fields\":[]}", mapper);
        material.setMaterialMeta("PET");
        assertThrows(RuntimeException.class, () -> MaterialMasterConfigurationService.requireMaterialCompatibility(metaOld, metaNew, material, mapper));
    }

    @Test void namingRuleCanChangeOnTemplateAlreadyInUse() {
        var old = schema(oldField);
        var next = MaterialAttributeSchema.parse(
                "{\"version\":1,\"meta_options\":[],\"default_unit\":\"PCS\",\"fields\":[" + oldField + "],"
                        + "\"name_pattern\":{\"fields\":[\"meta\",\"model\"],\"separator\":\"-\",\"empty\":\"skip\"}}",
                mapper);
        assertDoesNotThrow(() -> MaterialMasterConfigurationService.requireCompatibleUpdate(old, next, mapper));
    }

    @Test void renderingMetadataSurvivesSerializationAndUnknownKeysStillFail() throws Exception {
        var schema = schema(oldField.replace("\"required\":false", "\"required\":false,\"common\":true,\"in_spec\":false,\"spec_order\":9"));
        assertEquals(mapper.valueToTree(schema), mapper.valueToTree(MaterialAttributeSchema.parse(mapper.writeValueAsString(schema), mapper)));
        assertThrows(RuntimeException.class, () -> schema.validateAttributes(Map.of("unknown", 1), mapper));
    }

    @Test void saveTemplateActuallyPersistsCompatibleSchemaAndRejectsDestructiveEdits() throws Exception {
        var catalog = org.mockito.Mockito.mock(com.jjx.inventory.master.MaterialMasterCatalog.class);
        var locks = org.mockito.Mockito.mock(com.jjx.inventory.master.MaterialMasterMapper.class);
        var categories = org.mockito.Mockito.mock(com.jjx.inventory.mapper.InventoryMaterialCategoryMapper.class);
        var templates = org.mockito.Mockito.mock(com.jjx.inventory.master.AttributeTemplateMapper.class);
        var materials = org.mockito.Mockito.mock(com.jjx.inventory.mapper.InventoryMaterialMapper.class);
        var service = new MaterialMasterConfigurationService(catalog, locks, categories, templates, materials, mapper);
        org.mockito.Mockito.when(locks.lockCategory(2L)).thenReturn(2L);
        org.mockito.Mockito.when(locks.firstMaterialInCategory(2L)).thenReturn(99L);
        var referenced = new com.jjx.inventory.domain.InventoryMaterial();
        referenced.setMaterialId(99L); referenced.setMaterialCode("RM000099");
        referenced.setAttributeJson("{\"force_g\":280}");
        org.mockito.Mockito.when(locks.templateReferences(2L, 0L)).thenReturn(java.util.List.of(referenced));
        org.mockito.Mockito.when(catalog.categories()).thenReturn(java.util.List.of(
                new com.jjx.inventory.master.MaterialMasterCatalog.Category(2L, "RAW_AUX_ELECTRONIC", "电子元件", 1L, 3, "R", "原材料/辅材/电子元件", true, 1, 1)));
        var existing = new com.jjx.inventory.master.InventoryAttributeTemplate();
        existing.setTemplateId(7L);
        existing.setAttributeSchema(mapper.writeValueAsString(schema(oldField)));
        org.mockito.Mockito.when(locks.lockTemplate(2L)).thenReturn(existing);
        var next = schema(oldField + ",{\"key\":\"has_dimple\",\"label\":\"带点\",\"type\":\"boolean\",\"common\":true,\"in_spec\":true}");
        var dto = new MaterialMasterConfigurationService.TemplateSave("TPL_ELECTRONIC", "电子元件", 2L, next, com.jjx.common.enums.StatusEnum.NORMAL.getCode());
        service.saveTemplate(dto, "Codex");
        org.mockito.Mockito.verify(templates).updateById(existing);
        assertTrue(existing.getAttributeSchema().contains("has_dimple"));
        assertThrows(RuntimeException.class, () -> service.saveTemplate(
                new MaterialMasterConfigurationService.TemplateSave("TPL_ELECTRONIC", "电子元件", 2L, schema(oldField.replace("gf", "g")), com.jjx.common.enums.StatusEnum.NORMAL.getCode()), "Codex"));
        org.mockito.Mockito.verify(templates, org.mockito.Mockito.times(1)).updateById(existing);
    }
}
