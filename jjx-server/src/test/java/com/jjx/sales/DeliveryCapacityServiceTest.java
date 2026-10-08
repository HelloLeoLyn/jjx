package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.OrderStockReserveService;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.DeliveryPendingQuantityVO;
import com.jjx.sales.enums.SalesDeliveryStatusEnum;
import com.jjx.sales.mapper.DeliveryArrangeMapper;
import com.jjx.sales.service.DeliveryCapacityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryCapacityServiceTest {
    @Mock InventoryItemMapper itemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock DeliveryArrangeMapper arrangeMapper;
    @Mock OrderStockReserveService reserveService;
    @InjectMocks DeliveryCapacityService service;

    private SalesOrderProduct source(long id,long orderId) {
        SalesOrderProduct p=new SalesOrderProduct();p.setId(id);p.setOrderId(orderId);p.setProductId(2L);p.setQuantity(1000);return p;
    }
    private SalesDeliveryItem line(long sourceId,int quantity) {
        SalesDeliveryItem l=new SalesDeliveryItem();l.setOrderProductId(sourceId);l.setQuantity(quantity);return l;
    }
    private void stock(int total,int reserved,boolean lock) {
        InventoryItem item=new InventoryItem();item.setInventoryItemId(2089L);
        when(itemMapper.selectBySource(anyString(),eq(2L))).thenReturn(item);
        InventoryStock stock=new InventoryStock();stock.setTotalQuantity(BigDecimal.valueOf(total));stock.setTotalReserved(BigDecimal.valueOf(reserved));
        if(lock) when(stockMapper.selectByInventoryItemIdForUpdate(2089L)).thenReturn(stock);
        else when(stockMapper.selectByInventoryItemId(2089L)).thenReturn(stock);
    }
    private DeliveryPendingQuantityVO pending(long orderId,int quantity) {
        var p=new DeliveryPendingQuantityVO();p.setOrderId(orderId);p.setQuantity(BigDecimal.valueOf(quantity));return p;
    }
    @Test void own700FinishedPiecesAllow700ButReject1000() {
        stock(700,700,true);when(reserveService.getReservedQty(5L)).thenReturn(Map.of(2L,BigDecimal.valueOf(700)));
        var p=source(4,5);var capacity=service.capacities(List.of(p),true);
        assertEquals(BigDecimal.valueOf(700),capacity.get(2L).forOrder(5L));
        service.validate(List.of(line(4,700)),Map.of(4L,p),capacity);
        assertThrows(BusinessException.class,()->service.validate(List.of(line(4,1000)),Map.of(4L,p),capacity));
        verify(stockMapper).selectByInventoryItemIdForUpdate(2089L);
        verify(stockMapper,never()).updateById(any(InventoryStock.class));
    }
    @Test void ownPending200Leaves500WithoutDeductingPhysicalStockTwice() {
        stock(700,700,false);when(reserveService.getReservedQty(5L)).thenReturn(Map.of(2L,BigDecimal.valueOf(700)));
        when(arrangeMapper.pendingByOrder(2L,SalesDeliveryStatusEnum.PENDING.getValue())).thenReturn(List.of(pending(5,200)));
        assertEquals(BigDecimal.valueOf(500),service.capacities(List.of(source(4,5)),false).get(2L).forOrder(5L));
    }
    @Test void posted200Leaves500OncePendingOccupationHasGone() {
        stock(500,500,false);when(reserveService.getReservedQty(5L)).thenReturn(Map.of(2L,BigDecimal.valueOf(500)));
        assertEquals(BigDecimal.valueOf(500),service.capacities(List.of(source(4,5)),false).get(2L).forOrder(5L));
    }
    @Test void otherOrdersReserved700CannotBeUsedByThisOrder() {
        stock(700,700,false);
        assertEquals(BigDecimal.ZERO,service.capacities(List.of(source(4,5)),false).get(2L).forOrder(5L));
    }
    @Test void multipleOrdersCannotEachSpendTheSameShared700() {
        stock(700,0,true);var a=source(4,5);var b=source(6,7);var capacity=service.capacities(List.of(a,b),true);
        var sources=Map.of(4L,a,6L,b);
        assertThrows(BusinessException.class,()->service.validate(List.of(line(4,400),line(6,400)),sources,capacity));
        service.validate(List.of(line(4,400),line(6,300)),sources,capacity);
    }
    @Test void sameProductOnTwoLinesOfAnOrderDoesNotDuplicateItsReservation() {
        stock(700,700,true);when(reserveService.getReservedQty(5L)).thenReturn(Map.of(2L,BigDecimal.valueOf(700)));
        var a=source(4,5);var b=source(6,5);var capacity=service.capacities(List.of(a,b),true);
        assertThrows(BusinessException.class,()->service.validate(List.of(line(4,400),line(6,400)),Map.of(4L,a,6L,b),capacity));
    }
    @Test void otherPendingShipmentConsumesSharedStockAfterItsOwnReservation() {
        stock(1000,700,false);when(reserveService.getReservedQty(5L)).thenReturn(Map.of());when(reserveService.getReservedQty(7L)).thenReturn(Map.of(2L,BigDecimal.valueOf(700)));
        when(arrangeMapper.pendingByOrder(2L,SalesDeliveryStatusEnum.PENDING.getValue())).thenReturn(List.of(pending(7,800)));
        assertEquals(BigDecimal.valueOf(200),service.capacities(List.of(source(4,5)),false).get(2L).forOrder(5L));
    }
    @Test void missingInventoryIdentityHasNoCapacity() {
        assertEquals(BigDecimal.ZERO,service.capacities(List.of(source(4,5)),false).get(2L).forOrder(5L));
    }
}
