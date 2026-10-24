package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.classify.*;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialClassifyTest {
    private MaterialClassifyRuleMapper rules;
    private InventoryMaterialMapper materials;
    private MaterialMasterMapper locks;
    private MaterialMasterCatalog catalog;
    private MaterialClassifyService service;
    private MaterialMasterCatalog.Configuration config;
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), InventoryMaterial.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MaterialClassifyRule.class);
        rules=mock(MaterialClassifyRuleMapper.class); materials=mock(InventoryMaterialMapper.class);
        locks=mock(MaterialMasterMapper.class); catalog=mock(MaterialMasterCatalog.class);
        service=new MaterialClassifyService(rules, materials, locks, catalog, new ObjectMapper());
        var category = new MaterialMasterCatalog.Category(3L,"RAW_AUX_PROTECT","保护膜",2L,3,"R","原材料/辅材/保护膜",true,1,1);
        var template = new InventoryAttributeTemplate(); template.setTemplateId(33L);
        config = new MaterialMasterCatalog.Configuration(category,List.of(category),template,
                new MaterialAttributeSchema(1,List.of(),false,null,null,"PCS",List.of()));
    }
    private MaterialClassifyRule rule(long id, String keyword, MaterialClassifyRule.MatchField field, boolean enabled) {
        var rule=new MaterialClassifyRule(); rule.setId(id);rule.setKeyword(keyword);rule.setMatchField(field);
        rule.setCategoryCode("RAW_AUX_PROTECT");rule.setEnabled(enabled);rule.setPriority(10);return rule;
    }
    private void target() {
        when(catalog.categories()).thenReturn(List.of(config.category())); when(catalog.configuration(3L)).thenReturn(config);
    }
    private void targetLocks() { when(locks.lockCategory(3L)).thenReturn(3L);when(locks.lockTemplate(3L)).thenReturn(config.template()); }

    @Test void nameSpecAnyDisabledAndLiteralKeywordAreRespected() {
        target();
        when(rules.selectList(any())).thenReturn(List.of(rule(1,"保护膜",MaterialClassifyRule.MatchField.NAME,false),
                rule(2,"PC",MaterialClassifyRule.MatchField.SPEC,true),rule(3,"保护膜",MaterialClassifyRule.MatchField.NAME,true)));
        assertEquals(3L,service.match("PET保护膜","透明").ruleId());
        assertEquals(2L,service.match("PET保护膜","pc").ruleId());
        assertNull(service.match("PC","透明"));
        when(rules.selectList(any())).thenReturn(List.of(rule(4,"[PC]",MaterialClassifyRule.MatchField.ANY,true)));
        assertNull(service.match("PC","透明"));assertEquals(4L,service.match("普通膜","[pc]").ruleId());
    }

    @Test void batchAccepts200AndReturnsMissingRowsButRejects201BeforeAnyLock() {
        targetLocks(); when(catalog.configuration(3L)).thenReturn(config);
        var result=service.classify(new MaterialClassifyService.BatchRequest(LongStream.rangeClosed(1,200).boxed().toList(),3L),"codex");
        assertEquals(200,result.failCount());assertEquals(0,result.successCount());
        verify(locks,times(200)).lockMaterial(anyLong());clearInvocations(locks);
        assertThrows(BusinessException.class,()->service.classify(new MaterialClassifyService.BatchRequest(LongStream.rangeClosed(1,201).boxed().toList(),3L),"codex"));
        verifyNoInteractions(locks);verifyNoInteractions(materials);
    }
    @Test void batchBindsTemplateAndManualConfirmationAndKeepsValidationFailures() {
        targetLocks();when(catalog.configuration(3L)).thenReturn(config);
        var good=new InventoryMaterial();good.setMaterialId(1L);good.setAttributeJson("{}");
        var bad=new InventoryMaterial();bad.setMaterialId(2L);bad.setAttributeJson("{\"stale\":1}");
        when(locks.lockMaterial(1L)).thenReturn(good);when(locks.lockMaterial(2L)).thenReturn(bad);
        when(materials.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var result=service.classify(new MaterialClassifyService.BatchRequest(List.of(1L,2L,3L),3L),"codex");
        assertEquals(1,result.successCount());assertEquals(2,result.failCount());
        var update=ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(materials).update(isNull(),update.capture());
        var values=update.getValue().getParamNameValuePairs().values();
        assertTrue(values.containsAll(List.of(3L,33L,"MANUAL",true,"codex")));
    }
    @Test void defaultRulePreviewDoesNotWriteAndUsesCurrentValues() {
        target();when(rules.selectList(any())).thenReturn(List.of(rule(1,"保护膜",MaterialClassifyRule.MatchField.NAME,true)));
        var material=new InventoryMaterial();material.setMaterialId(1L);material.setMaterialName("PET保护膜");material.setRemark("【原名称】无关旧名【原规格】透明");
        when(materials.selectList(any())).thenReturn(List.of(material));
        var result=service.applyRules(false,"codex");assertTrue(result.dryRun());assertEquals(1,result.changes().size());
        verifyNoInteractions(locks);verify(materials,never()).update(any(),any());
        var query=ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);verify(materials).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("category_id IS NULL"));
    }
    @Test void confirmedApplyDoesNotOverwriteConcurrentManualClassification() {
        target();targetLocks();when(rules.selectList(any())).thenReturn(List.of(rule(1,"保护膜",MaterialClassifyRule.MatchField.ANY,true)));
        var material=new InventoryMaterial();material.setMaterialId(1L);material.setMaterialName("保护膜");
        when(materials.selectList(any())).thenReturn(List.of(material));
        var current=new InventoryMaterial();current.setCategoryId(9L);when(locks.lockMaterial(1L)).thenReturn(current);
        var result=service.applyRules(true,"codex");assertFalse(result.dryRun());assertTrue(result.changes().isEmpty());assertEquals(1,result.failures().size());
        verify(materials,never()).update(any(),any());
    }
    @Test void allEndpointsUseMaterialEditPermissionAndApplyDefaultsToPreview() throws Exception {
        var type=MaterialClassifyController.class;
        assertArrayEquals(new String[]{"inventory:material:edit"},type.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        var parameter=type.getMethod("apply",boolean.class).getParameters()[0].getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
        assertEquals("false",parameter.defaultValue());
    }
}
