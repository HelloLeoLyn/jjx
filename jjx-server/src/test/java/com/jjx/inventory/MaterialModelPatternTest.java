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
                "R", "原材料/PET膜/片", true, 1, 1);
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
    @Test void duplicatesAreRejectedInCreateAndEditButCategoryAndSelfAreScopedInQuery() {
        var duplicate = new InventoryMaterial(); duplicate.setModel("PET125"); duplicate.setCategoryId(2L);
        // Simulate existing rows only in category 2, and inspect the actual generated predicate below.
        when(materials.selectList(any(Wrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<InventoryMaterial> q = call.getArgument(0);
            q.getSqlSegment();
            return q.getParamNameValuePairs().containsValue(2L) ? List.of(duplicate) : List.of();
        });
        assertTrue(assertThrows(BusinessException.class, () -> service.save(draft(), "codex")).getMessage().contains("同品类型号重复"));
        verify(materials, never()).insert(any(InventoryMaterial.class));
        configure(3L, pattern);
        var other = draft(); other.setCategoryId(3L);
        assertEquals("PET125", service.save(other, "codex").getModel());
        var old = new InventoryMaterial(); old.setMaterialId(7L); old.setUnit("PCS");
        when(materials.selectById(7L)).thenReturn(old); when(locks.lockMaterial(7L)).thenReturn(old);
        var edit = draft(); edit.setMaterialId(7L); edit.setModel("PET125");
        assertThrows(BusinessException.class, () -> service.save(edit, "codex"));
        var queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materials, times(3)).selectList(queries.capture());
        var query = queries.getValue();
        assertTrue(query.getSqlSegment().contains("category_id ="));
        assertTrue(query.getSqlSegment().contains("model ="));
        assertTrue(query.getSqlSegment().contains("material_id <>"));
        assertTrue(query.getSqlSegment().endsWith("FOR UPDATE"));
        assertTrue(query.getParamNameValuePairs().containsValue(7L));
    }
    @Test void emptyModelsNeverParticipateInUniqueness() {
        configure(2L, "null"); service.save(draft(), "codex");
        verify(materials, never()).selectList(any(Wrapper.class));
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
