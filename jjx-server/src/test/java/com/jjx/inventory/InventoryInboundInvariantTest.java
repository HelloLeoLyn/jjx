package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryInboundInvariantTest {
    @Mock InventoryInboundOrderMapper inboundOrderMapper;
    @Mock InventoryInboundItemMapper inboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @Mock ProductionOrderMapper productionOrderMapper;
    @InjectMocks InventoryInboundServiceImpl service;

    @Test void completedInboundCannotBeConfirmedAgain() {
        when(inboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(inbound(OrderStatusEnum.COMPLETED));
        assertFalse(service.confirm(1L, 9L, "tester"));
        verify(inboundItemMapper, never()).selectByInboundId(any());
        verify(stockItemMapper, never()).insert(any(InventoryStockItem.class));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(inboundOrderMapper, never()).updateById(any(InventoryInboundOrder.class));
    }

    @Test void pendingInboundCannotSkipApprovalAndConfirm() {
        when(inboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(inbound(OrderStatusEnum.PENDING));
        assertFalse(service.confirm(1L, 9L, "tester"));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(inboundOrderMapper, never()).updateById(any(InventoryInboundOrder.class));
    }

    @Test void completedInboundCannotBeCancelled() {
        when(inboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(inbound(OrderStatusEnum.COMPLETED));
        assertFalse(service.cancel(1L, "duplicate cancel"));
        verify(inboundOrderMapper, never()).updateById(any(InventoryInboundOrder.class));
    }

    @Test void onlyPendingInboundCanBeApproved() {
        when(inboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(inbound(OrderStatusEnum.DRAFT));
        assertFalse(service.approve(1L, 9L, "tester", "approve"));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(inboundOrderMapper, never()).updateById(any(InventoryInboundOrder.class));
    }

    @Test void unfinishedProductionOrderCannotCreateFinishedGoodsInbound() {
        ProductionOrder order = new ProductionOrder();
        order.setOrderStatus(com.jjx.production.enums.OrderStatusEnum.IN_PROGRESS.getValue());
        when(productionOrderMapper.selectById(7L)).thenReturn(order);
        assertThrows(BusinessException.class, () -> service.createFromProduction(7L));
        verify(inboundOrderMapper, never()).insert(any(InventoryInboundOrder.class));
        verify(inboundItemMapper, never()).insert(any(InventoryInboundItem.class));
    }

    @Test void existingProductionInboundIsNotCreatedTwice() {
        ProductionOrder order = new ProductionOrder();
        order.setOrderStatus(com.jjx.production.enums.OrderStatusEnum.COMPLETED.getValue());
        order.setOrderNo("WO-001");
        when(productionOrderMapper.selectById(7L)).thenReturn(order);
        when(inboundOrderMapper.selectCount(any())).thenReturn(1L);
        assertNull(service.createFromProduction(7L));
        verify(inboundOrderMapper, never()).insert(any(InventoryInboundOrder.class));
        verify(inboundItemMapper, never()).insert(any(InventoryInboundItem.class));
    }

    private static InventoryInboundOrder inbound(OrderStatusEnum status) {
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundId(1L);
        order.setOrderStatus(status.getValue());
        return order;
    }
}
