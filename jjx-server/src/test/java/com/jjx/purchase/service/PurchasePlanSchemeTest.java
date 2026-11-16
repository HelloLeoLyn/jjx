package com.jjx.purchase.service;

import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.entity.PurchaseSupplier;
import com.jjx.purchase.domain.enums.PurchasePlanSchemeStatus;
import com.jjx.purchase.mapper.PurchasePlanContextMapper;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import com.jjx.purchase.service.impl.PurchasePlanEntryService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 订单真实数量/金额规则，Mapper替身；不写业务库、不宣称已完成替代领料闭环。 */
class PurchasePlanSchemeTest {
    InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    PurchaseSupplierMapper suppliers = mock(PurchaseSupplierMapper.class);
    PurchasePlanContextMapper context = mock(PurchasePlanContextMapper.class);
    PurchaseOrderServiceImpl orders = mock(PurchaseOrderServiceImpl.class, CALLS_REAL_METHODS);
    PurchasePlanEntryService service = new PurchasePlanEntryService(materials, context, orders, suppliers);
    InventoryMaterial source;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(orders, "materialMapper", materials);
        source = material(110L, "195", "50000");
        source.setSupplierId(3L); source.setSupplierName("原主要供应商");
        var supplier = new PurchaseSupplier(); supplier.setSupplierId(16L); supplier.setSupplierName("本次供应商乙"); supplier.setStatus(StatusEnum.NORMAL.getCode());
        when(suppliers.selectById(16L)).thenReturn(supplier);
    }
    InventoryMaterial material(long id, String width, String length) {
        var m = new InventoryMaterial(); m.setMaterialId(id); m.setMaterialCode("M" + id); m.setMaterialName("物料" + id);
        m.setStatus(StatusEnum.NORMAL.getCode()); m.setUnit("㎡"); m.setWidthMm(new BigDecimal(width)); m.setLengthMm(new BigDecimal(length));
        when(materials.selectById(id)).thenReturn(m); return m;
    }
    PurchaseOrderItemDTO item(long id, String quantity) {
        var item = new PurchaseOrderItemDTO(); item.setMaterialId(id); item.setPurchaseUnit("ROLL"); item.setPurchaseQuantity(new BigDecimal(quantity));
        item.setRollLengthM(new BigDecimal("50")); item.setPriceUnit("ROLL"); item.setUnitPrice(new BigDecimal("100")); item.setTaxRate(new BigDecimal("13"));
        return item;
    }
    PurchasePlanEntryService.SchemeRequest request(PurchaseOrderItemDTO item, Long supplier, BigDecimal demand) {
        return new PurchasePlanEntryService.SchemeRequest(110L, demand, supplier, LocalDate.of(2026,11,20), LocalDate.of(2026,11,21), item);
    }
    void eq(String expected, BigDecimal value) { assertEquals(0, new BigDecimal(expected).compareTo(value)); }
    @Test void sameMaterialSupplierOverrideUsesCanonicalMoneyAndDoesNotChangeArchive() {
        var input = item(110, "2"); input.setQuantity(new BigDecimal("999")); input.setAmount(new BigDecimal("999999")); input.setUnit("KG");
        var r = service.scheme(request(input, 16L, new BigDecimal("10.5")));
        assertEquals(PurchasePlanSchemeStatus.READY, r.status()); assertEquals("本次供应商乙", r.supplierName());
        eq("19.5", r.item().getQuantity()); eq("200", r.item().getAmount()); eq("26", r.item().getTaxAmount());
        eq("10.5", r.coveredQuantity()); eq("0", r.remainingQuantity()); assertTrue(r.message().contains("晚于需求日期"));
        assertEquals(3L, source.getSupplierId()); verify(materials, never()).updateById(any(InventoryMaterial.class)); verifyNoInteractions(context);
    }
    @Test void insufficientQuantityKeepsExactRemainingDemand() {
        var r = service.scheme(request(item(110,"1"), 16L, new BigDecimal("10.5")));
        eq("9.75", r.coveredQuantity()); eq("0.75", r.remainingQuantity());
    }
    @Test void otherWidthCannotCoverOriginalByAreaOrName() {
        material(354, "1000", "50000");
        var r = service.scheme(request(item(354,"1"), 16L, new BigDecimal("10.5")));
        assertEquals(PurchasePlanSchemeStatus.NEEDS_CONFIRMATION, r.status()); assertNull(r.coveredQuantity()); assertNull(r.remainingQuantity());
        eq("50",r.item().getQuantity()); assertTrue(r.message().contains("不能生成替代采购单"));
    }
    @Test void identicalDimensionsUnderDifferentMaterialIdsStillNeedConfirmation() {
        material(355,"195","50000");
        assertEquals(PurchasePlanSchemeStatus.NEEDS_CONFIRMATION,service.scheme(request(item(355,"2"),16L,new BigDecimal("10.5"))).status());
    }
    @Test void missingSupplierDoesNotPretendReady() {
        var r=service.scheme(request(item(110,"2"),null,new BigDecimal("10.5")));
        assertEquals(PurchasePlanSchemeStatus.UNARRANGED,r.status()); assertNull(r.supplierName());
    }
    @Test void invalidAndDisabledSuppliersCannotBeSelected() {
        assertThrows(BusinessException.class,()->service.scheme(request(item(110,"2"),999L,BigDecimal.ONE)));
        when(suppliers.selectById(16L)).thenReturn(new PurchaseSupplier());
        assertThrows(BusinessException.class,()->service.scheme(request(item(110,"2"),16L,BigDecimal.ONE)));
    }
    @Test void knownWidthAndWholeRollRulesRemainEnforced() {
        var changed=item(110,"2");changed.setRollWidthMm(new BigDecimal("200"));
        assertThrows(BusinessException.class,()->service.scheme(request(changed,16L,BigDecimal.ONE)));
        assertThrows(BusinessException.class,()->service.scheme(request(item(110,"0.5"),16L,BigDecimal.ONE)));
    }
    @Test void unknownDemandStaysUnknownAndNegativeDemandIsRejected() {
        var r=service.scheme(request(item(110,"1"),16L,null));assertNull(r.coveredQuantity());assertNull(r.remainingQuantity());
        assertThrows(BusinessException.class,()->service.scheme(request(item(110,"1"),16L,new BigDecimal("-1"))));
    }
    @Test void disabledSourceCannotBeUsedToValidateProcurement() {
        source.setStatus(StatusEnum.DISABLE.getCode());
        assertThrows(BusinessException.class,()->service.scheme(request(item(110,"1"),16L,BigDecimal.ONE)));
    }
}
