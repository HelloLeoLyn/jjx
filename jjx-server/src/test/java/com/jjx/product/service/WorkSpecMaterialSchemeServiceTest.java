package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class WorkSpecMaterialSchemeServiceTest {
    EngineeringBomMapper mapper = mock(EngineeringBomMapper.class);
    IEngineeringBomService boms = mock(IEngineeringBomService.class);
    WorkSpecMaterialSchemeService service = new WorkSpecMaterialSchemeService(mapper, boms, new ObjectMapper());

    EngineeringBom scheme(long id, boolean current) {
        var bom = new EngineeringBom(); bom.setBomId(id); bom.setBomCode("MFG-" + id);
        bom.setBomVersion("V1.0"); bom.setOutputType("MATERIAL"); bom.setOutputId(351L);
        bom.setIsCurrent(current); bom.setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue()); return bom;
    }
    EngineeringBomItem output(long item, Long selected) {
        var row = new EngineeringBomItem(); row.setItemId(item); row.setMaterialId(351L);
        row.setMaterialName("3M 9080-W195-L80"); row.setSourceType("buy"); row.setSubBomId(selected); return row;
    }
    EngineeringBomVO root(EngineeringBomItem... rows) {
        var root = new EngineeringBomVO(); root.setItems(List.of(rows)); return root;
    }
    void inputs(long id) {
        var raw = new EngineeringBomItem(); raw.setMaterialId(110L); raw.setMaterialName("3M 9080");
        when(boms.getBomItems(id)).thenReturn(List.of(raw));
    }
    @Test void buyRowWithoutBindingUsesApprovedCurrentManufacturingScheme() {
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true))); inputs(1);
        var result = service.resolve(root(output(28, null)));
        assertEquals("3M 9080", result.path("byItem").path("28").path("items").get(0).path("materialName").asText());
        assertEquals(1, result.path("byMaterial").path("351").path("bomId").asLong());
    }
    @Test void explicitSchemeWinsOverProductPrimaryAndCurrent() {
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true), scheme(2, false))); inputs(2);
        var root = root(output(28, 2L));
        var choice = new ManufacturingSchemeChoiceDTO(); choice.setMaterialId(351L); choice.setBomId(1L); choice.setIsPrimary(true);
        root.setSchemeChoices(List.of(choice));
        assertEquals(2, service.resolve(root).path("byItem").path("28").path("bomId").asLong());
        verify(boms, never()).getBomItems(1L);
    }
    @Test void productPrimaryWinsOverCurrentAndSharedSchemeReadOnlyOnce() {
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true), scheme(2, false))); inputs(2);
        var root = root(output(28, null), output(29, null));
        var choice = new ManufacturingSchemeChoiceDTO(); choice.setMaterialId(351L); choice.setBomId(2L); choice.setIsPrimary(true);
        root.setSchemeChoices(List.of(choice));
        assertEquals(2, service.resolve(root).path("byMaterial").path("351").path("bomId").asLong());
        verify(boms, times(1)).getBomItems(2L);
    }
    @Test void invalidExplicitSelectionDoesNotSilentlyUseCurrent() {
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true)));
        var entry = service.resolve(root(output(28, 999L))).path("byItem").path("28");
        assertTrue(entry.path("warning").asText().contains("不存在或产出不匹配"));
        assertFalse(entry.has("items")); verifyNoInteractions(boms);
    }
    @Test void unapprovedSchemeIsExplicitlyReported() {
        var draft = scheme(1, true); draft.setApproveStatus(ProductEnums.BomStatus.DRAFT.getValue());
        when(mapper.selectList(any())).thenReturn(List.of(draft));
        assertTrue(service.resolve(root(output(28, 1L))).path("byItem").path("28").path("warning").asText().contains("尚未批准"));
    }
    @Test void explicitSchemeForAnotherMaterialCannotBecomeThisRowsSource() {
        var wrong = scheme(2, true); wrong.setOutputId(352L);
        when(mapper.selectList(any())).thenReturn(List.of(wrong));
        assertTrue(service.resolve(root(output(28, 2L))).path("byItem").path("28").path("warning").asText().contains("产出不匹配"));
        verifyNoInteractions(boms);
    }
    @Test void noSchemeKeepsOriginalButAmbiguousCurrentIsReported() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.resolve(root(output(28, null))).path("byItem").path("28").isEmpty());
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true), scheme(2, true)));
        assertTrue(service.resolve(root(output(28, null))).path("byItem").path("28").path("warning").asText().contains("多个"));
    }
    @Test void sameMaterialWithDifferentExplicitSchemesKeepsRowIdentity() {
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true), scheme(2, false))); inputs(1); inputs(2);
        var result = service.resolve(root(output(28, 1L), output(29, 2L)));
        assertEquals(1, result.path("byItem").path("28").path("bomId").asLong());
        assertEquals(2, result.path("byItem").path("29").path("bomId").asLong());
        assertTrue(result.path("byMaterial").path("351").path("warning").asText().contains("不同生产BOM"));
    }
    @Test void changedManufacturingInputInvalidatesWorkingSpecConfirmation() {
        when(mapper.selectList(any())).thenReturn(List.of(scheme(1, true))); inputs(1);
        var specMapper = mock(com.jjx.product.mapper.ProductWorkSpecMapper.class);
        when(specMapper.exists(2L)).thenReturn(1);
        var products = mock(IProductService.class);
        var full = new com.jjx.product.domain.vo.ProductFullVO();
        var product = new com.jjx.product.domain.vo.ProductVo(); product.setProductId(2L);
        full.setProduct(product); full.setBom(root(output(28, null)));
        when(products.getFullProductDetail(2L)).thenReturn(full);
        var workSpec = new ProductWorkSpecService(specMapper, null, null, products, new ObjectMapper(), null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(workSpec, "materialSchemes", service);
        String revision = workSpec.get(2L).path("sourceRevision").asText();
        when(specMapper.read(2L)).thenReturn("{\"confirmedSource\":\"" + revision + "\"}");
        assertTrue(workSpec.get(2L).path("approved").asBoolean());
        var changed = new EngineeringBomItem(); changed.setMaterialId(110L); changed.setMaterialName("修改后的制造原料");
        when(boms.getBomItems(1L)).thenReturn(List.of(changed));
        var after = workSpec.get(2L);
        assertNotEquals(revision, after.path("sourceRevision").asText());
        assertFalse(after.path("approved").asBoolean());
    }
}
