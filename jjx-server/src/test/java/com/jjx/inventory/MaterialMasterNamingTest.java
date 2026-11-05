package com.jjx.inventory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.MaterialMasterController;
import com.jjx.inventory.master.MaterialMasterMapper;
import com.jjx.inventory.master.MaterialMasterService;
import com.jjx.inventory.master.MaterialMasterCatalog;
import com.jjx.inventory.master.MaterialMasterDTO;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.jjx.inventory.master.MaterialNamingPattern;
import com.jjx.system.annotation.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialMasterNamingTest {
    private final MaterialMasterMapper locks = mock(MaterialMasterMapper.class);
    private final InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final MaterialMasterCatalog catalog = mock(MaterialMasterCatalog.class);
    private final MaterialMasterService service = new MaterialMasterService(catalog, locks, materials, null,
            null, null, mock(EventPublisher.class), json);
    private InventoryMaterial existing;

    @BeforeAll static void initMaterialMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "naming-test"), InventoryMaterial.class);
    }

    @BeforeEach void setup() {
        existing = new InventoryMaterial(); existing.setMaterialId(1L); existing.setMaterialCode("RM001");
        existing.setMaterialName("人工名称"); existing.setSpecification("旧规格");
        when(locks.lockMaterial(1L)).thenReturn(existing);
    }

    @Test void onlyNamingAndAuditColumnsAreWrittenAndLogContainsLockedOldValues() throws Exception {
        when(materials.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(materials.selectById(1L)).thenReturn(existing);
        var result = service.updateNaming(1L, new MaterialMasterService.NamingPatch(" 新名称 ", " ", "厚度重算"), "codex");
        var update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(materials).update(isNull(), update.capture());
        String columns = update.getValue().getSqlSet();
        assertEquals(List.of("material_name", "specification", "update_by", "update_time"),
                java.util.Arrays.stream(columns.split(",")).map(s -> s.substring(0, s.indexOf('='))).toList());
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("旧规格"), "未归类保留历史规格并忽略外部文本");
        assertFalse(result.specificationGeneration().generated());
        assertTrue(result.specificationGeneration().message().contains("未生成"));
        var detail = json.readTree(result.detail()).get("changes");
        assertEquals("名称：人工名称 → 新名称", detail.get(0).asText());
        assertEquals("重算依据：厚度重算", detail.get(1).asText());
        var method = MaterialMasterController.class.getMethod("updateNaming", Long.class, MaterialMasterService.NamingPatch.class);
        assertArrayEquals(new String[]{"inventory:material:edit"}, method.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        var context = new StandardEvaluationContext();
        context.setVariable("result", com.jjx.common.core.result.Result.success(result));
        assertEquals(result.detail(), new SpelExpressionParser().parseExpression(method.getAnnotation(Log.class).detail()).getValue(context));
    }

    @Test void rejectsMissingMaterialBlankNameAndOversizedValuesWithoutWriting() {
        assertThrows(BusinessException.class, () -> service.updateNaming(2L, new MaterialMasterService.NamingPatch("名称", null, null), "codex"));
        assertThrows(BusinessException.class, () -> service.updateNaming(1L, null, "codex"));
        for (String name : java.util.Arrays.asList(null, " ", "名".repeat(201)))
            assertThrows(BusinessException.class, () -> service.updateNaming(1L, new MaterialMasterService.NamingPatch(name, null, null), "codex"));
        assertThrows(BusinessException.class, () -> service.updateNaming(1L, new MaterialMasterService.NamingPatch("名称", null, "据".repeat(1001)), "codex"));
        verifyNoInteractions(materials);
    }

    @Test void unchangedValuesDoNotWriteAndFailedUpdateThrows() {
        service.updateNaming(1L, new MaterialMasterService.NamingPatch(" 人工名称 ", "旧规格", null), "codex");
        verifyNoInteractions(materials);
        assertThrows(BusinessException.class, () -> service.updateNaming(1L, new MaterialMasterService.NamingPatch("新名称", null, null), "codex"));
    }

    @Test void evenOversizedExternalSpecificationIsIgnoredAndCannotClearHistory() {
        var result = service.updateNaming(1L,
                new MaterialMasterService.NamingPatch("人工名称", "伪造".repeat(1000), null), "codex");
        assertEquals("旧规格", result.material().getSpecification());
        assertFalse(result.specificationGeneration().generated());
        verifyNoInteractions(materials);
    }

    @Test void rejectsEveryNonNamingInputEvenWhenGlobalUnknownPropertiesAreIgnored() throws Exception {
        json.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertEquals("名称", json.readValue("{\"materialName\":\"名称\",\"specification\":\"规格\"}", MaterialMasterService.NamingPatch.class).materialName());
        for (String field : List.of("categoryId", "attributeJson", "templateId", "supplierId", "safeStock", "materialCode", "materialId", "updateBy"))
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue(
                    "{\"materialName\":\"名称\",\"" + field + "\":1}", MaterialMasterService.NamingPatch.class), field);
    }

    private MaterialNamingPattern configureColorNaming() {
        var pattern = new MaterialNamingPattern(List.of("brand", "model", "color"), "-", "skip");
        var schema = new MaterialAttributeSchema(1, List.of(), false, null, null, null, List.of(), pattern);
        var category = new MaterialMasterCatalog.Category(1L, "TEST", "测试品类", null, 3,
                "R", "测试品类", true, null, null);
        when(catalog.optionalConfiguration(1L)).thenReturn(
                new MaterialMasterCatalog.Configuration(category, List.of(category), null, schema));
        when(catalog.dictionaryLabel("brand", "BOMING", false)).thenReturn("博明");
        existing.setBrand("BOMING"); existing.setModel("125"); existing.setCategoryId(1L);
        when(materials.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        return pattern;
    }

    @ParameterizedTest
    @CsvSource({"BROWN,茶色", "BLACK,黑色", "WHITE,白色", "SILVER,银色", "TRANSPARENT,透明",
            "UNKNOWN_COLOR,UNKNOWN_COLOR", "蓝色,蓝色"})
    void allNamingPreviewsUseDictionaryLabelsWithoutWriting(String code, String expectedColor) {
        var pattern = configureColorNaming();
        existing.setColor(code);
        if (code.equals(expectedColor)) {
            when(catalog.dictionaryLabel("color", code, false)).thenThrow(new BusinessException("字典选项不存在"));
        } else {
            when(catalog.dictionaryLabel("color", code, false)).thenReturn(expectedColor);
        }
        var draft = new MaterialMasterDTO();
        draft.setBrand(existing.getBrand()); draft.setModel(existing.getModel()); draft.setColor(code);
        String expected = "博明-125-" + expectedColor;
        assertEquals(expected, service.renderNaming(1L, draft));
        assertEquals(expected, service.previewNaming(1L, pattern).samples().get(0).newName());
        var preview = service.previewRename(1L, 5);
        assertEquals(expected, preview.samples().get(0).newName());
        assertEquals(1, preview.changeable());
        assertEquals("人工名称", existing.getMaterialName(), "预览不得改写存量名称");
        verify(catalog, times(3)).dictionaryLabel("color", code, false);
        verify(materials, never()).update(any(), any(Wrapper.class));
        verify(locks, never()).lockMaterial(anyLong());
    }

    @Test void blankOrMissingDictionaryLabelFallsBackToOriginalColor() {
        configureColorNaming();
        var draft = new MaterialMasterDTO(); draft.setColor("BROWN");
        when(catalog.dictionaryLabel("color", "BROWN", false)).thenReturn(null, " ");
        assertEquals("BROWN", service.renderNaming(1L, draft));
        assertEquals("BROWN", service.renderNaming(1L, draft));
        draft.setColor(null);
        assertNull(service.renderNaming(1L, draft));
        verify(catalog, never()).dictionaryLabel("color", null, false);
    }

    @Test void applyingDictionaryColorRenameOnlyWritesDifferentRowsAndKeepsAudit() throws Exception {
        configureColorNaming();
        existing.setColor("BROWN");
        var unchanged = new InventoryMaterial();
        unchanged.setMaterialId(2L); unchanged.setBrand("BOMING"); unchanged.setModel("125");
        unchanged.setColor("BROWN"); unchanged.setMaterialName("博明-125-茶色");
        when(catalog.dictionaryLabel("color", "BROWN", false)).thenReturn("茶色");
        when(materials.selectList(any(Wrapper.class))).thenReturn(List.of(existing, unchanged));
        when(materials.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(materials.selectById(1L)).thenReturn(existing);

        assertEquals(1, service.applyRename(1L, "codex"));
        verify(locks).lockMaterial(1L);
        verify(locks, never()).lockMaterial(2L);
        var update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(materials).update(isNull(), update.capture());
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("博明-125-茶色"));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("codex"));
        assertEquals("博明-125-茶色", unchanged.getMaterialName());
    }
}
