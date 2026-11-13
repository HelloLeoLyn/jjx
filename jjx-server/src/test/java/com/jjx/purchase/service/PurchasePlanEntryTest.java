package com.jjx.purchase.service;

import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.enums.ReceiptStatusEnum;
import com.jjx.purchase.mapper.PurchasePlanContextMapper;
import com.jjx.purchase.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实订单换算服务+Mapper替身；不声称写过业务库。尺寸取本地真实档案。 */
class PurchasePlanEntryTest {
    InventoryMaterialMapper materials=mock(InventoryMaterialMapper.class);
    PurchasePlanContextMapper context=mock(PurchasePlanContextMapper.class);
    PurchaseOrderServiceImpl orders=mock(PurchaseOrderServiceImpl.class,CALLS_REAL_METHODS);
    PurchasePlanEntryService service=new PurchasePlanEntryService(materials,context,orders);
    @BeforeEach void init(){ReflectionTestUtils.setField(orders,"materialMapper",materials);}
    InventoryMaterial material(long id,String unit,String w,String l){
        var m=new InventoryMaterial();m.setMaterialId(id);m.setUnit(unit);
        m.setMaterialCode("M"+id);m.setMaterialName("本地档案");
        m.setWidthMm(new BigDecimal(w));m.setLengthMm(new BigDecimal(l));when(materials.selectById(id)).thenReturn(m);return m;
    }
    PurchaseOrderItemDTO input(long id,String u,String qty){var i=new PurchaseOrderItemDTO();i.setMaterialId(id);i.setPurchaseUnit(u);i.setPurchaseQuantity(new BigDecimal(qty));return i;}
    void eq(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    @Test void realEbaRollQuantityDelegatesToOrderRule(){
        material(354,"㎡","1220","100000");var i=input(354,"ROLL","3");i.setRollLengthM(new BigDecimal("100"));
        i.setUnit("KG");i.setPriceUnit("PCS");i.setUnitPrice(new BigDecimal("999"));
        var r=service.quantity(i);eq("366",r.getQuantity());eq("0",r.getAmount());eq("3",r.getPurchaseQuantity());assertEquals("㎡",r.getUnit());
    }
    @Test void fractionalAreaDemandIsNotRoundedToInteger(){material(110,"㎡","195","50000");var r=service.quantity(input(110,"㎡","0.156"));eq("0.156",r.getQuantity());}
    @Test void pieceStockRemainsPieces(){material(109,"PCS","450","225");var r=service.quantity(input(109,"PCS","100"));eq("100",r.getQuantity());assertEquals("PCS",r.getUnit());}
    @Test void fractionalRollCannotSilentlyBecomeAWholeRoll(){material(110,"㎡","195","50000");var i=input(110,"ROLL","0.5");i.setRollLengthM(new BigDecimal("50"));assertThrows(BusinessException.class,()->service.quantity(i));}
    @Test void knownWidthCannotChangeUnderSameMaterial(){material(110,"㎡","195","50000");var i=input(110,"ROLL","2");i.setRollLengthM(new BigDecimal("50"));i.setRollWidthMm(new BigDecimal("200"));assertThrows(BusinessException.class,()->service.quantity(i));}
    @Test void missingRollLengthCannotBeParsedFromSpecification(){var m=material(110,"㎡","195","50000");m.setSpecification("195*50000mm");assertThrows(BusinessException.class,()->service.quantity(input(110,"ROLL","2")));}
    @Test void invalidMaterialCannotUseClientUnitAsFallback(){assertThrows(BusinessException.class,()->service.quantity(input(999,"KG","2")));verifyNoInteractions(context);}
    @Test void contextUsesNamedActiveStatesAndDoesNotWriteStock(){
        material(110,"㎡","195","50000");when(context.stock(110L)).thenReturn(new BigDecimal("19.5"));when(context.available(110L)).thenReturn(new BigDecimal("9.75"));
        when(context.inTransit(org.mockito.ArgumentMatchers.eq(110L),anyList(),anyList())).thenReturn(new BigDecimal("29.25"));
        var r=service.material(110L);eq("19.5",(BigDecimal)r.get("currentStock"));eq("9.75",(BigDecimal)r.get("availableStock"));eq("29.25",(BigDecimal)r.get("inTransit"));eq("50",(BigDecimal)r.get("rollLengthM"));
        verify(context).inTransit(110L,List.of(ApproveStatusEnum.DRAFT.getValue(),ApproveStatusEnum.PENDING.getValue(),ApproveStatusEnum.APPROVED.getValue()),List.of(ReceiptStatusEnum.PENDING.getValue(),ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue()));
    }
    @Test void stockQueryFailureIsNotPretendedZero(){material(110,"㎡","195","50000");when(context.stock(110L)).thenThrow(new IllegalStateException("offline"));assertThrows(IllegalStateException.class,()->service.material(110L));}
}
