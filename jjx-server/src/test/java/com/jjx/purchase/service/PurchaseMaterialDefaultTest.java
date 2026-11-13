package com.jjx.purchase.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseMaterialDefaultDTO;
import com.jjx.purchase.mapper.PurchaseMaterialDefaultMapper;
import com.jjx.purchase.service.impl.PurchaseMaterialDefaultService;
import com.jjx.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PurchaseMaterialDefaultTest {
    final PurchaseMaterialDefaultMapper defaults=mock(PurchaseMaterialDefaultMapper.class);
    final InventoryMaterialMapper materials=mock(InventoryMaterialMapper.class);
    final PurchaseMaterialDefaultService service=new PurchaseMaterialDefaultService(defaults,materials,mock(SysDictItemMapper.class),new ObjectMapper());
    PurchaseMaterialDefaultTest() {
        when(defaults.supplierExists(16L)).thenReturn(1);
        var m=new InventoryMaterial();m.setUnit("M2");m.setWidthMm(new BigDecimal("1220"));m.setLengthMm(new BigDecimal("100000"));when(materials.selectById(354L)).thenReturn(m);
        when(defaults.write(eq(16L),anyString(),anyString())).thenReturn(1);
    }
    PurchaseMaterialDefaultDTO dto() {var d=new PurchaseMaterialDefaultDTO();d.setSupplierId(16L);d.setMaterialId(354L);d.setPurchaseUnit("卷");d.setPriceUnit("平方");return d;}
    @Test void dimensionsAloneNeverGuessRollPurchasing() {
        var r=service.read(16L,354L);assertEquals(false,r.get("configured"));assertEquals("㎡",r.get("purchaseUnit"));assertEquals(new BigDecimal("100"),r.get("rollLengthM"));verify(defaults,never()).write(anyLong(),anyString(),anyString());
    }
    @Test void storedAgreementAutomaticallyReturnsRollAndAreaUnits() {
        when(defaults.read(16L)).thenReturn("{\"354\":{\"purchaseUnit\":\"ROLL\",\"priceUnit\":\"㎡\"}}");
        var r=service.read(16L,354L);assertEquals(true,r.get("configured"));assertEquals("ROLL",r.get("purchaseUnit"));assertEquals("㎡",r.get("priceUnit"));
    }
    @Test void updatesMergeUnderSupplierLockAndKeepOtherMaterialDefaults() {
        when(defaults.readForUpdate(16L)).thenReturn("{\"110\":{\"purchaseUnit\":\"M\",\"priceUnit\":\"㎡\"}}");
        service.save(dto(),"operator");
        verify(defaults).write(eq(16L),argThat(s->s.contains("\"110\"")&&s.contains("\"354\"")&&s.contains("ROLL")),eq("operator"));
        var order=inOrder(defaults);order.verify(defaults).readForUpdate(16L);order.verify(defaults).write(eq(16L),anyString(),eq("operator"));
    }
    @Test void dictionaryAreaAliasUsesCanonicalAreaCodeAndLabel() {
        var dictionary=mock(SysDictItemMapper.class);var option=new com.jjx.system.domain.entity.SysDictItem();option.setItemValue("M2");option.setLabel("M2");
        when(dictionary.selectList(any())).thenReturn(java.util.List.of(option));
        var s=new PurchaseMaterialDefaultService(defaults,materials,dictionary,new ObjectMapper());
        assertEquals(java.util.Map.of("value","㎡","label","㎡"),s.units().get(0));
    }
    @Test void malformedDefaultsAndUnknownSupplierFailClearly() {
        when(defaults.read(16L)).thenReturn("[]");assertThrows(BusinessException.class,()->service.read(16L,354L));
        assertThrows(BusinessException.class,()->service.read(999L,354L));
    }
}
