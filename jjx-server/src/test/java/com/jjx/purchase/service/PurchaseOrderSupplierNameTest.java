package com.jjx.purchase.service;

import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.purchase.converter.PurchaseConverter;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.purchase.domain.vo.PurchaseOrderItemVO;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** dev-20261110-008：快照跨档案变更稳定，旧单安全回退。替身 mapper 不代表真实库验收。 */
class PurchaseOrderSupplierNameTest {
    PurchaseOrderServiceImpl service;
    InventoryMaterialMapper materials;
    PurchaseOrderItemMapper items;
    InventoryMaterial material;

    @BeforeEach void setup() {
        service = mock(PurchaseOrderServiceImpl.class, CALLS_REAL_METHODS);
        materials = mock(InventoryMaterialMapper.class);
        items = mock(PurchaseOrderItemMapper.class);
        ReflectionTestUtils.setField(service, "materialMapper", materials);
        ReflectionTestUtils.setField(service, "orderItemMapper", items);
        ReflectionTestUtils.setField(service, "purchaseConverter", Mappers.getMapper(PurchaseConverter.class));
        material = new InventoryMaterial();
        material.setMaterialId(7L);
        material.setSupplierMaterialName("旧供应商品名");
        when(materials.selectById(7L)).thenReturn(material);
    }

    PurchaseOrderItem item(Long id, String snapshot) {
        var item = new PurchaseOrderItem();
        item.setMaterialId(id);
        item.setSupplierMaterialName(snapshot);
        return item;
    }

    @Test void savedSnapshotSurvivesMasterChangeAndNewOrderTakesNewName() {
        var dto = new PurchaseOrderItemDTO();
        dto.setMaterialId(7L);
        var saved = new java.util.ArrayList<PurchaseOrderItem>();
        doAnswer(call -> { saved.add(call.getArgument(0)); return 1; }).when(items).insert(any(PurchaseOrderItem.class));
        ReflectionTestUtils.invokeMethod(service, "saveOrderItems", 1L, List.of(dto));
        material.setSupplierMaterialName("新供应商品名");
        ReflectionTestUtils.invokeMethod(service, "saveOrderItems", 2L, List.of(dto));
        assertEquals("旧供应商品名", read(List.of(saved.get(0))).get(0).getSupplierMaterialName());
        assertEquals("新供应商品名", saved.get(1).getSupplierMaterialName());
        verify(materials, never()).selectBatchIds(anyCollection());
    }

    @Test void snapshotOverridesClientValueAndMissingMaterialClearsIt() {
        var existing = item(7L, "伪造品名");
        ReflectionTestUtils.invokeMethod(service, "fillSupplierMaterialName", existing);
        assertEquals("旧供应商品名", existing.getSupplierMaterialName());
        for (Long id : new Long[] {null, 999L}) {
            var missing = item(id, "客户端值");
            ReflectionTestUtils.invokeMethod(service, "fillSupplierMaterialName", missing);
            assertNull(missing.getSupplierMaterialName());
        }
    }

    @Test void onlyMissingSnapshotsFallBackAndNullMasterNamesAreSafe() {
        var unnamed = new InventoryMaterial();
        unnamed.setMaterialId(8L);
        when(materials.selectBatchIds(anyCollection())).thenReturn(List.of(material, unnamed));
        var result = read(List.of(item(7L, "已固化"), item(7L, null), item(8L, ""), item(null, null)));
        assertEquals("已固化", result.get(0).getSupplierMaterialName());
        assertEquals("旧供应商品名", result.get(1).getSupplierMaterialName());
        assertNull(result.get(2).getSupplierMaterialName());
        assertNull(result.get(3).getSupplierMaterialName());
        verify(items, never()).updateById(any(PurchaseOrderItem.class));
    }

    @Test void emptyDetailsAndMissingIdsNeedNoMasterLookup() {
        assertTrue(read(List.of()).isEmpty());
        assertNull(read(List.of(item(null, null))).get(0).getSupplierMaterialName());
        verifyNoInteractions(materials);
    }

    @Test void copiedOrderTakesCurrentNameInsteadOfSourceSnapshot() {
        var orders = mock(PurchaseOrderMapper.class);
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        when(orders.selectById(1L)).thenReturn(new PurchaseOrder());
        when(orders.insert(any(PurchaseOrder.class))).thenReturn(1);
        when(items.selectList(any())).thenReturn(List.of(item(7L, "源单快照")));
        doReturn("PO-TEST").when(service).generateOrderNo();
        service.copyOrder(1L);
        verify(items).insert(argThat((PurchaseOrderItem value) -> "旧供应商品名".equals(value.getSupplierMaterialName())));
    }

    @Test void bothSuggestionPlanEntryPointsTakeMasterSnapshot() {
        var orders = mock(PurchaseOrderMapper.class);
        var alerts = mock(InventoryAlertService.class);
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        ReflectionTestUtils.setField(service, "alertService", alerts);
        doReturn("PO-TEST").when(service).generateOrderNo();
        when(alerts.generatePurchaseSuggestions()).thenReturn(List.of(java.util.Map.of(
                "materialId", 7L, "suggestQuantity", 2, "sourceAlertId", 3L,
                "supplierMaterialName", "过期建议品名")));
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.createPlanFromSuggestions();
            material.setSupplierMaterialName("改档后品名");
            service.createPlanFromAlerts(List.of(3L));
        }
        var captured = org.mockito.ArgumentCaptor.forClass(PurchaseOrderItem.class);
        verify(items, times(2)).insert(captured.capture());
        assertEquals("旧供应商品名", captured.getAllValues().get(0).getSupplierMaterialName());
        assertEquals("改档后品名", captured.getAllValues().get(1).getSupplierMaterialName());
    }

    List<PurchaseOrderItemVO> read(List<PurchaseOrderItem> values) {
        return ReflectionTestUtils.invokeMethod(service, "toItemVOListWithSupplierMaterialName", values);
    }
}
