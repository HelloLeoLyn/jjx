package com.jjx.purchase.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.converter.PurchaseConverter;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.entity.*;
import com.jjx.purchase.mapper.*;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 金额唯一实现、基本单位数量不变及采购保存无库存写入。Mapper替身不代表真实库。 */
class PurchaseOrderDualUnitTest {
    static BigDecimal bd(String n) { return new BigDecimal(n); }
    PurchaseOrderItemDTO line(String purchase, String price, String quantity, String unitPrice) {
        var i = new PurchaseOrderItemDTO(); i.setMaterialId(7L); i.setUnit("㎡");
        i.setPurchaseUnit(purchase); i.setPriceUnit(price); i.setQuantity(bd(quantity)); i.setUnitPrice(bd(unitPrice));
        i.setRollLengthM(bd("50")); i.setRollWidthMm(bd("195")); i.setRollCount(5); return i;
    }
    @Test void rollOrderWithAreaPriceAndAreaOrderWithRollPrice() {
        var a = line("卷", "㎡", "0", "10");
        PurchaseOrderServiceImpl.calculateItemPricing(a);
        assertEquals(bd("487.50"), a.getAmount()); assertEquals(bd("10.00"), a.getEquivalentUnitPrice());
        assertEquals(bd("48.7500"), a.getQuantity());
        var b = line("㎡", "卷", "48.75", "500");
        PurchaseOrderServiceImpl.calculateItemPricing(b);
        assertEquals(5, b.getRollCount()); assertEquals(bd("2500.00"), b.getAmount());
        assertEquals(bd("51.28"), b.getEquivalentUnitPrice());
        var entity = new PurchaseOrderItem(); org.springframework.beans.BeanUtils.copyProperties(b, entity);
        assertEquals(bd("51.2821"), PurchaseOrderServiceImpl.baseUnitCost(entity));
    }
    @Test void rollAreaPriceTwentyYieldsNineSevenFive() {
        var i = line("卷", "㎡", "0", "20"); PurchaseOrderServiceImpl.calculateItemPricing(i);
        assertEquals(bd("975.00"), i.getAmount());
        assertEquals(bd("48.7500"), i.getQuantity());
    }

    @Test void fractionalRollRoundsUpAndAmountsRoundToCents() {
        var i = line("㎡", "卷", "49", "500"); PurchaseOrderServiceImpl.calculateItemPricing(i);
        assertEquals(6, i.getRollCount()); assertEquals(bd("3000.00"), i.getAmount());
        i = line("㎡", "㎡", "1.005", "1"); PurchaseOrderServiceImpl.calculateItemPricing(i);
        assertEquals(bd("1.01"), i.getAmount());
    }
    @Test void missingDimensionsCannotSilentlyConvertAndNoAreaReturnsNull() {
        for (String purchase : List.of("卷", "㎡")) {
            var i = line(purchase, "卷".equals(purchase) ? "㎡" : "卷", "48.75", "10"); i.setRollLengthM(null);
            assertThrows(BusinessException.class, () -> PurchaseOrderServiceImpl.calculateItemPricing(i));
        }
        var i = line("㎡", "㎡", "0", "10"); PurchaseOrderServiceImpl.calculateItemPricing(i);
        assertNull(i.getEquivalentUnitPrice());
        i.setUnit("个"); i.setPurchaseUnit("个"); i.setPriceUnit("个"); i.setQuantity(bd("5"));
        PurchaseOrderServiceImpl.calculateItemPricing(i); assertNull(i.getEquivalentUnitPrice());
    }
    @Test void metreMaterialWritesCostPerMetre() {
        var i = line("卷", "卷", "0", "500"); i.setUnit("m"); PurchaseOrderServiceImpl.calculateItemPricing(i);
        assertEquals(bd("250.0000"), i.getQuantity()); assertEquals(bd("2500.00"), i.getAmount());
        var e = new PurchaseOrderItem(); org.springframework.beans.BeanUtils.copyProperties(i, e);
        assertEquals(bd("10.0000"), PurchaseOrderServiceImpl.baseUnitCost(e));
        assertEquals(bd("51.28"), i.getEquivalentUnitPrice());
    }
    @Test void changingKnownMaterialDimensionsRequiresAnotherMaterial() throws Exception {
        var c=PurchaseOrderServiceImpl.class.getConstructors()[0];
        var service=(PurchaseOrderServiceImpl)c.newInstance(java.util.Arrays.stream(c.getParameterTypes()).map(org.mockito.Mockito::mock).toArray());
        var mapper=mock(InventoryMaterialMapper.class);var material=new InventoryMaterial();material.setUnit("㎡");material.setWidthMm(bd("195"));material.setLengthMm(bd("50000"));
        when(mapper.selectById(7L)).thenReturn(material);ReflectionTestUtils.setField(service,"materialMapper",mapper);
        var dto=new PurchaseOrderDTO();var roll=line("ROLL","㎡","0","20");roll.setRollWidthMm(bd("200"));dto.setItems(List.of(roll));
        assertTrue(assertThrows(BusinessException.class,()->service.previewPricing(dto)).getMessage().contains("物料规格不一致"));
        material.setUnit("PCS");material.setWidthMm(bd("450"));material.setLengthMm(bd("225"));
        var piece=line("PCS","㎡","100","20");piece.setPurchaseQuantity(bd("100"));piece.setPieceWidthMm(bd("460"));piece.setPieceLengthMm(bd("225"));dto.setItems(List.of(piece));
        assertThrows(BusinessException.class,()->service.previewPricing(dto));
    }
    @Test void previewAndSaveUseMaterialDefaultsAndNeverCallStockOrInbound() throws Exception {
        var c = PurchaseOrderServiceImpl.class.getConstructors()[0];
        Object[] deps = java.util.Arrays.stream(c.getParameterTypes()).map(org.mockito.Mockito::mock).toArray();
        var service = (PurchaseOrderServiceImpl)c.newInstance(deps);
        var materials = mock(InventoryMaterialMapper.class); var m = new InventoryMaterial(); m.setUnit("㎡"); m.setWidthMm(bd("195"));
        when(materials.selectById(7L)).thenReturn(m); ReflectionTestUtils.setField(service,"materialMapper",materials);
        ReflectionTestUtils.setField(service,"purchaseConverter",Mappers.getMapper(PurchaseConverter.class));
        var items = mock(PurchaseOrderItemMapper.class); ReflectionTestUtils.setField(service,"orderItemMapper",items);
        var orders = mock(PurchaseOrderMapper.class); ReflectionTestUtils.setField(service,"orderMapper",orders);
        when(orders.insert(any(PurchaseOrder.class))).thenReturn(1);
        var dto = new PurchaseOrderDTO(); var i = line(null,null,"3","10"); i.setRollLengthM(null); i.setRollWidthMm(null);
        dto.setItems(List.of(i)); dto.setSupplierId(2L); dto.setSupplierName("供应商"); dto.setOrderNo("PO-TEST");
        service.previewPricing(dto); assertEquals(bd("30.00"),i.getAmount()); assertEquals("㎡",i.getPurchaseUnit());
        service.insertOrder(dto);
        verify(items).insert(argThat((PurchaseOrderItem e) -> "㎡".equals(e.getPurchaseUnit()) && "㎡".equals(e.getPriceUnit()) && bd("30.00").equals(e.getAmount())));
        ReflectionTestUtils.invokeMethod(service,"saveOrderItems",2L,List.of(i)); // 计划明细无卷维度仍可保存
        for (Object dep : deps) if (dep != null) {
            String name = org.mockito.Mockito.mockingDetails(dep).getMockCreationSettings().getTypeToMock().getSimpleName();
            if (name.matches("Inventory(Stock.*|TransactionMapper|InboundService|StockMutationService)")) verifyNoInteractions(dep);
        }
    }
}
