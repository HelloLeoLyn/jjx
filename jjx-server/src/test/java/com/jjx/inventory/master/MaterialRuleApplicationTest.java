package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialRuleApplicationTest {
    private final MaterialMasterService master = mock(MaterialMasterService.class);
    private final MaterialMasterCatalog catalog = mock(MaterialMasterCatalog.class);
    private final MaterialMasterMapper locks = mock(MaterialMasterMapper.class);
    private final InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    private final SysOperLogMapper logs = mock(SysOperLogMapper.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final MaterialRuleApplicationService service = new MaterialRuleApplicationService(master, catalog, locks, materials, logs, manager, json);
    private final MaterialRuleApplicationService.Options options = new MaterialRuleApplicationService.Options(26L, true, false, false);
    private List<InventoryMaterial> rows;
    private MaterialMasterCatalog.Configuration config;

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "rule-application"), InventoryMaterial.class);
    }
    @BeforeEach void setup() throws Exception {
        var schema = json.readValue("""
            {"version":1,"meta_options":[],"fields":[],"name_pattern":{"fields":["model"],"separator":"","empty":"skip"}}
            """, MaterialAttributeSchema.class);
        var category = new MaterialMasterCatalog.Category(26L,"FOAM","泡棉",null,3,"R","泡棉",true,0,1,"FOAM");
        var template = new InventoryAttributeTemplate(); template.setCategoryId(26L); template.setTemplateId(9L);
        config = new MaterialMasterCatalog.Configuration(category,List.of(category),template,schema);
        when(catalog.optionalConfiguration(26L)).thenReturn(config);
        when(locks.lockCategory(26L)).thenReturn(26L); when(locks.lockTemplate(26L)).thenReturn(template);
        rows = new ArrayList<>();
        for (long id=1; id<=45; id++) {
            var material = new InventoryMaterial(); material.setMaterialId(id); material.setCategoryId(26L);
            material.setMaterialCode("AUX"+id); material.setMaterialName("旧名"+id); material.setModel("M"+id);
            material.setSpecification("旧规格"); rows.add(material);
            when(locks.lockMaterial(id)).thenReturn(material);
        }
        when(materials.selectList(any(Wrapper.class))).thenAnswer(invocation -> List.copyOf(rows));
        when(master.readAttributes(any())).thenReturn(Map.of());
        when(master.renderNaming(eq(26L),any())).thenAnswer(invocation -> "新名"+((MaterialMasterDTO)invocation.getArgument(1)).getModel());
        when(master.saveRuleCandidate(any(),any(),anyString(),anyString(),any())).thenReturn(
                new MaterialMasterService.NamingResult(null,"{\"changes\":[\"名称已更新\"]}",null));
        when(logs.insert(any(com.jjx.system.domain.entity.SysOperLog.class))).thenReturn(1);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }
    private MaterialRuleApplicationService.Selection select(MaterialRuleApplicationService.Change row) {
        return new MaterialRuleApplicationService.Selection(row.materialId(),row.previewToken());
    }
    @Test void paginationReturnsOnlyTheRequestedDifferencesAndApplyOnlySelectedAcrossPages() {
        var first=service.preview(options,1,20,null); var third=service.preview(options,3,20,null);
        assertEquals(45,first.total()); assertEquals(20,first.records().size()); assertEquals(5,third.records().size());
        assertEquals(41L,third.records().get(0).materialId());
        var result=service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(select(first.records().get(1)),select(third.records().get(0)))),"codex",1L,"测试用户");
        assertEquals(2,result.success()); assertEquals(0,result.failed());
        verify(master).saveRuleCandidate(eq(rows.get(1)),any(),anyString(),eq("codex"),any());
        verify(master).saveRuleCandidate(eq(rows.get(40)),any(),anyString(),eq("codex"),any());
        verify(master,times(2)).saveRuleCandidate(any(),any(),anyString(),anyString(),any());
        verify(logs,times(2)).insert(any(com.jjx.system.domain.entity.SysOperLog.class));
        verify(manager,times(2)).commit(any());
        verify(master,never()).renderModel(any(),any(),any());
    }
    @Test void changedMaterialOrRuleCannotApplyUsingAnOldPreview() {
        var selected=select(service.preview(options,1,20,null).records().get(0));
        rows.get(0).setRemark("预览后资料已修改");
        var result=service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(selected)),"codex",1L,"测试用户");
        assertEquals(1,result.failed()); assertTrue(result.records().get(0).message().contains("已变化"));
        verify(master,never()).saveRuleCandidate(any(),any(),anyString(),anyString(),any()); verify(manager).rollback(any());
        rows.get(0).setRemark(null);
        config.template().setUpdateBy("another-user");
        assertEquals(1,service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(selected)),"codex",1L,"测试用户").failed());
    }
    @Test void categoryMismatchIsRejectedAndOneFailedRowDoesNotPreventAnotherFromCommitting() {
        var preview=service.preview(options,1,20,null);
        rows.get(0).setCategoryId(99L);
        var result=service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(select(preview.records().get(0)),select(preview.records().get(1)))),"codex",1L,"测试用户");
        assertEquals(1,result.failed()); assertEquals(1,result.success()); verify(manager).rollback(any()); verify(manager).commit(any());
    }
    @Test void invalidSelectionAndUnboundedPagesAreRejectedWithoutWrites() {
        assertThrows(BusinessException.class,()->service.preview(options,1,101,null));
        var selection=select(service.preview(options,1,20,null).records().get(0));
        assertThrows(BusinessException.class,()->service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(selection,selection)),"codex",1L,"测试用户"));
        assertThrows(BusinessException.class,()->service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of()),"codex",1L,"测试用户"));
        verifyNoInteractions(manager,logs);
    }
    @Test void modelUpdateRequiresExplicitScopeAndDependentNameUsesGeneratedModel() {
        var modelOptions = new MaterialRuleApplicationService.Options(26L,true,false,true);
        when(master.renderModel(eq(26L),any(),isNull())).thenReturn("NEW-MODEL");
        var preview = service.preview(modelOptions,1,20,null);
        assertEquals("NEW-MODEL",preview.records().get(0).newModel());
        assertEquals("新名NEW-MODEL",preview.records().get(0).newName());
        var result = service.apply(new MaterialRuleApplicationService.ApplyRequest(modelOptions,List.of(select(preview.records().get(0)))),"codex",1L,"测试用户");
        assertEquals(1,result.success());
        verify(master).saveRuleCandidate(eq(rows.get(0)),argThat(candidate -> "NEW-MODEL".equals(candidate.getModel()) && "旧规格".equals(candidate.getSpecification())),anyString(),eq("codex"),any());
    }
    @Test void unchangedRowsAreOmittedAndRepeatedApplySkipsWithoutWriting() {
        var selected=select(service.preview(options,1,20,null).records().get(0));
        when(master.renderNaming(eq(26L),any())).thenAnswer(invocation -> ((MaterialMasterDTO)invocation.getArgument(1)).getMaterialName());
        assertEquals(0,service.preview(options,1,20,null).total());
        assertEquals(1,service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(selected)),"codex",1L,"测试用户").skipped());
        verify(master,never()).saveRuleCandidate(any(),any(),anyString(),anyString(),any()); verifyNoInteractions(logs);
    }
    @Test void missingGenerationCannotBeSelectedAndAuditFailureRollsBack() {
        when(master.renderNaming(eq(26L),any())).thenReturn(null);
        var preview=service.preview(options,1,20,null);
        assertFalse(preview.records().get(0).selectable()); assertNull(preview.records().get(0).previewToken());
        when(master.renderNaming(eq(26L),any())).thenReturn("新名");
        var selected=select(service.preview(options,1,20,null).records().get(0));
        when(logs.insert(any(com.jjx.system.domain.entity.SysOperLog.class))).thenReturn(0);
        assertEquals(1,service.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(selected)),"codex",1L,"测试用户").failed());
        verify(manager).rollback(any()); verify(manager,never()).commit(any());
    }
}
