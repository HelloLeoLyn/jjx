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

    @Test void deletingChangingUnitsTypesOptionsOrAddingRequiredDefaultsIsRejected() {
        var old = schema(oldField);
        for (String candidate : new String[]{"", oldField.replace("gf", "g"), oldField.replace("decimal", "string"),
                oldField + ",{\"key\":\"new_field\",\"label\":\"新项\",\"type\":\"string\",\"required\":true}",
                oldField + ",{\"key\":\"new_field\",\"label\":\"新项\",\"type\":\"string\",\"default\":\"自动猜值\"}"}) {
            assertThrows(RuntimeException.class, () -> MaterialMasterConfigurationService.requireCompatibleUpdate(old, schema(candidate), mapper));
        }
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
