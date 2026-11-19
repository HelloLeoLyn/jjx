package com.jjx.inventory.location;

import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryTransferOrder;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.inventory.service.impl.InventoryTransferServiceImpl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocationSubdivisionTransferGuardTest {
    @Test void postedSubdivisionCannotReenterAnyGenericTransferLifecycle(){
        var orders=mock(InventoryTransferOrderMapper.class);var items=mock(InventoryTransferItemMapper.class);var mutations=mock(InventoryStockMutationService.class);
        var service=new InventoryTransferServiceImpl(orders,mock(EventPublisher.class),items,mock(InventoryStockItemMapper.class),mock(InventoryStockMapper.class),mock(InventoryTransactionMapper.class),mutations,mock(InventoryWarehouseMapper.class),mock(InventoryStorageLocationMapper.class));
        var order=new InventoryTransferOrder();order.setTransferId(7L);order.setTransferType("location");order.setTransferNo("LS001");order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());when(orders.selectById(7L)).thenReturn(order);
        assertThrows(BusinessException.class,()->service.submitApprove(7L));
        assertThrows(BusinessException.class,()->service.approve(7L,1L,"test","重复审批"));
        assertThrows(BusinessException.class,()->service.reject(7L,1L,"test","驳回"));
        assertThrows(BusinessException.class,()->service.confirmOut(7L,1L,"test"));
        assertThrows(BusinessException.class,()->service.confirmIn(7L,1L,"test"));
        assertThrows(BusinessException.class,()->service.cancel(7L,"取消"));
        assertThrows(BusinessException.class,()->service.updateStatus(7L,InventoryOrderStatusEnum.DRAFT.getValue()));
        verify(orders,never()).updateById(any(InventoryTransferOrder.class));verifyNoInteractions(items,mutations);
    }
}
