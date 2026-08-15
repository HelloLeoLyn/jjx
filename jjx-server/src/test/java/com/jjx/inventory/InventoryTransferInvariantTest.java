package com.jjx.inventory;

import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryTransferServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryTransferInvariantTest {
    @Mock InventoryTransferOrderMapper transferOrderMapper;
    @Mock InventoryTransferItemMapper transferItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @InjectMocks InventoryTransferServiceImpl service;

    @Test void alreadyTransferredOutOrderCannotBeTransferredOutAgain() {
        InventoryTransferOrder order = transfer(OrderStatusEnum.OUT_CONFIRM);
        order.setApproveStatus(OrderStatusEnum.APPROVED.getCode());
        when(transferOrderMapper.selectById(1L)).thenReturn(order);
        assertFalse(service.confirmOut(1L, 9L, "tester"));
        verify(transferItemMapper, never()).selectByTransferId(any());
        verify(stockItemMapper, never()).deductStock(any(), any());
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
    }

    @Test void transferCannotBeReceivedBeforeTransferOut() {
        when(transferOrderMapper.selectById(1L)).thenReturn(transfer(OrderStatusEnum.APPROVED));
        assertFalse(service.confirmIn(1L, 9L, "tester"));
        verify(stockItemMapper, never()).insert(any(InventoryStockItem.class));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
    }

    @Test void closedTransferCannotBeCancelled() {
        when(transferOrderMapper.selectById(1L)).thenReturn(transfer(OrderStatusEnum.CLOSED));
        assertFalse(service.cancel(1L, "late cancel"));
        verify(stockItemMapper, never()).insert(any(InventoryStockItem.class));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(transferOrderMapper, never()).updateById(any(InventoryTransferOrder.class));
    }

    @Test void transferOutCancellationWritesMatchingStockAndTransactionCompensation() {
        InventoryTransferOrder order = transfer(OrderStatusEnum.OUT_CONFIRM);
        order.setFromWarehouseId(10L);
        InventoryTransferItem item = transferItem(new BigDecimal("4"), new BigDecimal("4"));
        when(transferOrderMapper.selectById(1L)).thenReturn(order);
        when(transferItemMapper.selectByTransferId(1L)).thenReturn(List.of(item));
        when(transferOrderMapper.updateById(any(InventoryTransferOrder.class))).thenReturn(1);

        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(9L);
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            assertTrue(service.cancel(1L, "cancel in transit"));
        }

        var stockCaptor = org.mockito.ArgumentCaptor.forClass(InventoryStockItem.class);
        var txCaptor = org.mockito.ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(stockItemMapper).insert(stockCaptor.capture());
        verify(transactionMapper).insert(txCaptor.capture());
        assertEquals(new BigDecimal("4"), stockCaptor.getValue().getQuantity());
        assertEquals(stockCaptor.getValue().getQuantity(), txCaptor.getValue().getQuantity());
    }

    @Disabled("WI1-F01: confirmed rule requires COMPLETED transfers to reject cancellation")
    @Test void completedTransferCannotBeCancelled() {
        when(transferOrderMapper.selectById(1L)).thenReturn(transfer(OrderStatusEnum.COMPLETED));
        assertFalse(service.cancel(1L, "late cancel"));
    }

    @Disabled("WI1-F02: confirmed rule requires compensation to use outQuantity, not planned quantity")
    @Test void transferCancellationCompensatesActualOutQuantity() {
        InventoryTransferOrder order = transfer(OrderStatusEnum.OUT_CONFIRM);
        InventoryTransferItem item = transferItem(new BigDecimal("10"), new BigDecimal("4"));
        when(transferOrderMapper.selectById(1L)).thenReturn(order);
        when(transferItemMapper.selectByTransferId(1L)).thenReturn(List.of(item));
        service.cancel(1L, "partial transfer cancel");
        var captor = org.mockito.ArgumentCaptor.forClass(InventoryStockItem.class);
        verify(stockItemMapper).insert(captor.capture());
        assertEquals(new BigDecimal("4"), captor.getValue().getQuantity());
    }

    @Disabled("WI1-F03: confirmed rule requires repeated cancellation to be idempotent")
    @Test void repeatedTransferCancellationDoesNotCompensateTwice() {
        InventoryTransferOrder order = transfer(OrderStatusEnum.OUT_CONFIRM);
        InventoryTransferItem item = transferItem(new BigDecimal("4"), new BigDecimal("4"));
        when(transferOrderMapper.selectById(1L)).thenReturn(order);
        when(transferItemMapper.selectByTransferId(1L)).thenReturn(List.of(item));
        service.cancel(1L, "first");
        service.cancel(1L, "second");
        verify(stockItemMapper, times(1)).insert(any(InventoryStockItem.class));
    }

    private static InventoryTransferOrder transfer(OrderStatusEnum status) {
        InventoryTransferOrder order = new InventoryTransferOrder();
        order.setTransferId(1L);
        order.setOrderStatus(status.getCode());
        return order;
    }

    private static InventoryTransferItem transferItem(BigDecimal quantity, BigDecimal outQuantity) {
        InventoryTransferItem item = new InventoryTransferItem();
        item.setItemId(2L);
        item.setMaterialId(3L);
        item.setMaterialCode("MAT-3");
        item.setQuantity(quantity);
        item.setOutQuantity(outQuantity);
        return item;
    }
}
