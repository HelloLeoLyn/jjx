package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import org.junit.jupiter.api.Test;
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
class InventoryOutboundInvariantTest {
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @InjectMocks InventoryOutboundServiceImpl service;

    @Test void completedOutboundCannotBeConfirmedAgain() {
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(outbound(OrderStatusEnum.COMPLETED));
        assertFalse(service.confirm(1L, 9L, "tester"));
        verify(outboundItemMapper, never()).selectByOutboundId(any());
        verify(stockItemMapper, never()).deductStock(any(), any());
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(outboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
    }

    @Test void draftOutboundCannotSkipWorkflowAndConfirm() {
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(outbound(OrderStatusEnum.DRAFT));
        assertFalse(service.confirm(1L, 9L, "tester"));
        verify(stockItemMapper, never()).deductStock(any(), any());
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
    }

    @Test void insufficientStockDoesNotWriteTransactionOrTerminalStatus() {
        InventoryOutboundItem item = new InventoryOutboundItem();
        item.setMaterialId(11L);
        item.setMaterialCode("MAT-11");
        item.setQuantity(new BigDecimal("10"));
        InventoryStockItem batch = new InventoryStockItem();
        batch.setItemId(21L);
        batch.setQuantity(new BigDecimal("3"));
        batch.setReservedQuantity(BigDecimal.ZERO);
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(outbound(OrderStatusEnum.APPROVED));
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(item));
        when(stockItemMapper.selectFIFOAvailable(11L)).thenReturn(List.of(batch));
        assertThrows(BusinessException.class, () -> service.confirm(1L, 9L, "tester"));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(stockMapper, never()).refreshSummary(any());
        verify(outboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
    }

    private static InventoryOutboundOrder outbound(OrderStatusEnum status) {
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundId(1L);
        order.setOrderStatus(status.getValue());
        return order;
    }
}
