package com.jjx.purchase.service;

import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryAlertLog;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.service.impl.InventoryAlertServiceImpl;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseOrderSuggestionsSupplierNameTest {
    @Test void lowStockAndShortageBothExposeSupplierMaterialName() {
        var service = mock(InventoryAlertServiceImpl.class, CALLS_REAL_METHODS);
        var stocks = mock(InventoryStockMapper.class);
        var materials = mock(InventoryMaterialMapper.class);
        var alerts = mock(InventoryAlertLogMapper.class);
        ReflectionTestUtils.setField(service, "stockMapper", stocks);
        ReflectionTestUtils.setField(service, "materialMapper", materials);
        ReflectionTestUtils.setField(service, "alertLogMapper", alerts);
        ReflectionTestUtils.setField(service, "purchaseOrderItemMapper", mock(PurchaseOrderItemMapper.class));
        var stock = new InventoryStock();
        stock.setMaterialId(7L);
        stock.setTotalQuantity(BigDecimal.ZERO);
        when(stocks.selectLowStock()).thenReturn(List.of(stock));
        var shortage = new InventoryAlertLog();
        shortage.setMaterialId(8L);
        shortage.setSuggestion("建议补货 3");
        when(alerts.selectList(any())).thenReturn(List.of(shortage));
        for (long id : new long[] {7, 8}) {
            var material = new InventoryMaterial();
            material.setMaterialId(id);
            material.setSupplierMaterialName("供应商品名" + id);
            when(materials.selectById(id)).thenReturn(material);
        }
        var rows = service.generatePurchaseSuggestions();
        assertEquals(2, rows.size());
        assertEquals("供应商品名7", rows.get(0).get("supplierMaterialName"));
        assertEquals("供应商品名8", rows.get(1).get("supplierMaterialName"));
    }
}
