package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.system.service.ISysTagService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialModelPatternTest {
    final ObjectMapper json = new ObjectMapper();
    final MaterialMasterCatalog catalog = mock(MaterialMasterCatalog.class);
    final MaterialMasterMapper locks = mock(MaterialMasterMapper.class);
    final InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    final InventoryMaterialService codes = mock(InventoryMaterialService.class);
    final ISysTagService tags = mock(ISysTagService.class);
    final MaterialMasterService service = new MaterialMasterService(catalog, locks, materials, codes,
            null, tags, mock(EventPublisher.class), json);
    final String pattern = """
            {"fields":["meta","thickness_mm"],"separator":"","empty":"skip","units":{"thickness_mm":"μm"}}
            """;
    InventoryMaterial written;

    MaterialAttributeSchema schema(String rule) {
        return MaterialAttributeSchema.parse("""
                {"version":1,"meta_options":["PET"],"default_meta":"PET","default_unit":"PCS","fields":[],"model_pattern":%s}
                """.formatted(rule), json);
    }
    void configure(long categoryId, String rule) {
        var category = new MaterialMasterCatalog.Category(categoryId, "TEST" + categoryId, "PET膜/片", 1L, 3,
                "R", "原材料/PET膜/片", true, 1, 1, "PET");
        var template = new InventoryAttributeTemplate(); template.setTemplateId(categoryId);
        when(catalog.optionalConfiguration(categoryId)).thenReturn(new MaterialMasterCatalog.Configuration(
                category, List.of(category), template, schema(rule)));
        when(locks.lockCategory(categoryId)).thenReturn(categoryId);
    }
    MaterialMasterDTO draft() {
        var dto = new MaterialMasterDTO(); dto.setCategoryId(2L); dto.setMaterialName("测试名称");
        dto.setThicknessMm(new BigDecimal("0.125")); dto.setTagIds(List.of());
        return dto;
    }
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "model-test"), InventoryMaterial.class);
        configure(2L, pattern);
        when(catalog.dictionaries()).thenReturn(Map.of(
                "material_meta", List.of(new MaterialMasterCatalog.DictionaryOption("PET", "PET")),
                "unit", List.of(new MaterialMasterCatalog.DictionaryOption("PCS", "个"))));
        when(codes.generateCode(anyLong())).thenReturn("RMTEST");
        when(materials.insert(any(InventoryMaterial.class))).thenAnswer(call -> {
            written = call.getArgument(0); written.setMaterialId(100L); return 1;
        });
        when(materials.selectById(100L)).thenAnswer(call -> written);
        when(materials.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }
    @Test void newMaterialGeneratesAndExplicitManualModelIsSavedVerbatim() {
        assertEquals("PET125", service.save(draft(), "codex").getModel());
        var dto = draft(); dto.setModel("PET125A");
        assertEquals("PET125A", service.save(dto, "codex").getModel());
    }
    @Test void unconfiguredRuleAndEditingBlankOrExistingModelDoNotGenerate() {
        configure(2L, "null");
        assertNull(service.prepare(draft()).material().getModel());
        configure(2L, pattern);
        var old = new InventoryMaterial(); old.setMaterialId(7L); old.setModel("旧型号"); old.setUnit("PCS");
        when(materials.selectById(7L)).thenReturn(old); when(locks.lockMaterial(7L)).thenReturn(old);
        var dto = draft(); dto.setMaterialId(7L); dto.setModel("旧型号");
        assertEquals("旧型号", service.prepare(dto).material().getModel());
        service.save(dto, "codex");
        var update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(materials).update(isNull(), update.capture());
        String set = update.getValue().getSqlSet();
        var values = update.getValue().getParamNameValuePairs();
        assertTrue(set.contains("model=")); assertTrue(values.containsValue("旧型号"));
        dto.setModel(""); assertNull(service.prepare(dto).material().getModel());
    }
    @Test void duplicateModelsAreAllowedOnCreateAndEdit() {
        when(materials.selectList(any(Wrapper.class))).thenReturn(List.of());
        // 型号不再做唯一校验（2026-11-07 dev-20261107-003）：同型号不同供应商/规格可并存。
        assertEquals("PET125", service.save(draft(), "codex").getModel());
        configure(3L, pattern);
        var other = draft(); other.setCategoryId(3L);
        assertDoesNotThrow(() -> service.save(other, "codex"));
        var old = new InventoryMaterial(); old.setMaterialId(7L); old.setUnit("PCS");
        when(materials.selectById(7L)).thenReturn(old); when(locks.lockMaterial(7L)).thenReturn(old);
        var edit = draft(); edit.setMaterialId(7L); edit.setModel("PET125");
        assertDoesNotThrow(() -> service.save(edit, "codex"));
    }
    @Test void categoryPrefixIsUsedForGenerationAndPreviewAndMissingPrefixFails() {
        String rule = pattern.replace("meta", "categoryprefix");
        configure(2L, rule);
        var original = catalog.optionalConfiguration(2L);
        var category = new MaterialMasterCatalog.Category(2L, "RAW_AUX_RELEASE_FILM", "离型膜", 1L, 3,
                "R", "原材料/离型膜", true, 1, 1, "RF");
        when(catalog.optionalConfiguration(2L)).thenReturn(new MaterialMasterCatalog.Configuration(
                category, List.of(category), original.template(), schema(rule)));
        assertEquals("RF125", service.save(draft(), "codex").getModel());
        assertEquals("RF125", service.renderModel(2L, draft(), null));
        category = new MaterialMasterCatalog.Category(2L, "RAW_AUX_RELEASE_FILM", "离型膜", 1L, 3,
                "R", "原材料/离型膜", true, 1, 1, null);
        when(catalog.optionalConfiguration(2L)).thenReturn(new MaterialMasterCatalog.Configuration(
                category, List.of(category), original.template(), schema(rule)));
        assertTrue(assertThrows(BusinessException.class, () -> service.save(draft(), "codex")).getMessage().contains("配置型号前缀"));
        assertThrows(BusinessException.class, () -> service.renderModel(2L, draft(), null));
        // 即使操作员规则未选前缀，只要实际生成型号，缺前缀也必须报错。
        assertThrows(BusinessException.class, () -> service.renderModel(2L, draft(), new MaterialNamingPattern(List.of("meta"), "", "skip")));
        assertThrows(BusinessException.class, () -> MaterialNamingPattern.requireCategoryPrefix(" "));
    }
    @Test void duplicateCheckUsesFullKeyNotModel() {
        configure(2L, "null");
        when(materials.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.save(draft(), "codex");
        var queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materials).selectList(queries.capture());
        String sql = queries.getValue().getSqlSegment();
        assertTrue(sql.contains("material_name ="));
        assertFalse(sql.contains("model ="));
    }
    @Test void renderSharesUnitsAndDictionaryColorAndDoesNotWrite() {
        var dto = draft();
        assertEquals("PET125", service.renderModel(2L, dto, null));
        dto.setMaterialMeta("PC"); dto.setThicknessMm(new BigDecimal("0.254"));
        assertEquals("PC254", service.renderModel(2L, dto, null));
        dto.setColor("BROWN"); when(catalog.dictionaryLabel("color", "BROWN", false)).thenReturn("茶色");
        var rule = json.convertValue(Map.of("fields", List.of("meta", "color"), "separator", "-"), MaterialNamingPattern.class);
        assertEquals("PC-茶色", service.renderModel(2L, dto, rule));
        verifyNoInteractions(materials);
    }
    @Test void modelSelfReferenceAndUnknownTokensFailButUsedTemplateCanChangeModelRule() throws Exception {
        for (String invalid : List.of(pattern.replace("thickness_mm", "MODEL"), pattern.replace("thickness_mm", "missing")))
            assertThrows(BusinessException.class, () -> schema(invalid));
        var old = schema("null"); var next = schema(pattern);
        assertDoesNotThrow(() -> MaterialMasterConfigurationService.requireCompatibleUpdate(old, next, json));
        var restored = MaterialAttributeSchema.parse(json.writeValueAsString(next), json);
        assertEquals(next, restored);
        assertThrows(BusinessException.class, () -> service.renderModel(2L, draft(), new MaterialNamingPattern(List.of("model"), "", "skip")));
    }
    @Test void savingTemplateWithModelSelfReferenceIsRejectedBeforeWriting() {
        var categoryMapper = mock(com.jjx.inventory.mapper.InventoryMaterialCategoryMapper.class);
        var templateMapper = mock(AttributeTemplateMapper.class);
        var settings = new MaterialMasterConfigurationService(catalog, locks, categoryMapper, templateMapper, materials, json);
        var config = catalog.optionalConfiguration(2L);
        when(catalog.categories()).thenReturn(List.of(config.category()));
        var invalid = new MaterialAttributeSchema(1, List.of("PET"), false, "PET", null, "PCS", List.of(),
                null, null, new MaterialNamingPattern(List.of("MoDeL"), "", "skip"));
        var error = assertThrows(BusinessException.class, () -> settings.saveTemplate(
                new MaterialMasterConfigurationService.TemplateSave("TPL_PET", "PET", 2L, invalid, 1), "codex"));
        assertTrue(error.getMessage().contains("型号自身"));
        verifyNoInteractions(templateMapper);
    }

}
