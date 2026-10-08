package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryItemService;
import com.jjx.inventory.service.OrderStockReserveService;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesDeliveryOutboundReservationTest {
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryWarehouseMapper outboundWarehouseMapper;
    @Mock SalesDeliveryMapper salesDeliveryMapper;
    @Mock SalesDeliveryItemMapper salesDeliveryItemMapper;
    @Mock OrderMapper salesOrderMapper;
    @Mock OrderStockReserveService orderStockReserveService;
    @Mock InventoryItemService inventoryItemService;
    @Spy @InjectMocks InventoryOutboundServiceImpl service;

    private void stock(int owned, int lines) {
        SalesDelivery delivery = new SalesDelivery(); delivery.setOrderId(1L); delivery.setDeliveryNo("DL261008001");
        when(salesDeliveryMapper.selectById(20L)).thenReturn(delivery);
        SalesDeliveryItem line = new SalesDeliveryItem(); line.setProductId(26L); line.setProductCode("JST003MEOO"); line.setQuantity(2);
        when(salesDeliveryItemMapper.selectList(any())).thenReturn(lines == 1 ? List.of(line) : List.of(line, line));
        SalesOrder order = new SalesOrder(); order.setOrderId(1L); order.setOrderNo("SO261007001");
        when(salesOrderMapper.selectById(1L)).thenReturn(order);
        InventoryWarehouse warehouse = new InventoryWarehouse(); warehouse.setWarehouseId(1L);
        when(outboundWarehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(outboundOrderMapper.insert(any(InventoryOutboundOrder.class))).thenAnswer(inv -> {
            ((InventoryOutboundOrder) inv.getArgument(0)).setOutboundId(40L); return 1;
        });
        when(orderStockReserveService.getReservedQty(1L)).thenReturn(Map.of(26L, BigDecimal.valueOf(owned)));
        InventoryItem item = new InventoryItem(); item.setInventoryItemId(100L); item.setItemCode("JST003MEOO");
        when(inventoryItemService.ensure(any(), any(), any(), any(), any(), any())).thenReturn(item);
        InventoryStock stock = new InventoryStock(); stock.setTotalQuantity(BigDecimal.valueOf(2)); stock.setTotalReserved(BigDecimal.valueOf(2));
        when(stockMapper.selectByInventoryItemId(100L)).thenReturn(stock);
    }
    @Test void thisOrdersReservedStockCanBeShipped() {
        stock(2, 1);
        doReturn(true).when(service).approve(eq(40L), isNull(), isNull(), anyString());
        doReturn(true).when(service).confirm(eq(40L), isNull(), anyString());
        assertEquals(40L, service.createFromSalesByDelivery(20L));
        ArgumentCaptor<InventoryOutboundItem> item = ArgumentCaptor.forClass(InventoryOutboundItem.class);
        verify(outboundItemMapper).insert(item.capture()); assertEquals(BigDecimal.valueOf(2), item.getValue().getQuantity());
    }
    @Test void anotherOrdersReservationCannotBeUsed() {
        stock(0, 1);
        assertThrows(BusinessException.class, () -> service.createFromSalesByDelivery(20L));
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
    }
    @Test void repeatedProductLinesCannotReuseSameAvailableQuantity() {
        stock(2, 2);
        assertThrows(BusinessException.class, () -> service.createFromSalesByDelivery(20L));
        verify(service, never()).confirm(any(), any(), any());
    }
    @Test void falseApprovalDoesNotBecomeSuccessfulShipment() {
        stock(2, 1);
        doReturn(false).when(service).approve(eq(40L), isNull(), isNull(), anyString());
        assertThrows(BusinessException.class, () -> service.createFromSalesByDelivery(20L));
        verify(service, never()).confirm(any(), any(), any());
    }
    @Test void falseConfirmationDoesNotBecomeSuccessfulShipment() {
        stock(2, 1);
        doReturn(true).when(service).approve(eq(40L), isNull(), isNull(), anyString());
        doReturn(false).when(service).confirm(eq(40L), isNull(), anyString());
        assertThrows(BusinessException.class, () -> service.createFromSalesByDelivery(20L));
    }
}
