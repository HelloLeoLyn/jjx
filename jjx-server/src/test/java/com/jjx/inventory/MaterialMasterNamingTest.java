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
import com.jjx.system.annotation.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private final MaterialMasterService service = new MaterialMasterService(mock(com.jjx.inventory.master.MaterialMasterCatalog.class), locks, materials, null,
            null, null, mock(EventPublisher.class), json);
    private InventoryMaterial existing;

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
}
