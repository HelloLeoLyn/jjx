package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.inventory.service.InventoryStockMutationService;
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
    @Mock InventoryStockMutationService stockMutationService;
    @Mock com.jjx.sales.service.SalesDeliveryWorkflowService deliveryWorkflow;
    @Mock com.jjx.event.EventPublisher eventPublisher;
    @InjectMocks InventoryOutboundServiceImpl service;

    @Test void completedOutboundCannotBeConfirmedAgain() {
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(outbound(InventoryOrderStatusEnum.COMPLETED));
        assertFalse(service.confirm(1L, 9L, "tester"));
        verify(outboundItemMapper, never()).selectByOutboundId(any());
        verify(stockMutationService, never()).applyDelta(any(), any(), any());
        verify(stockMutationService, never()).applyDelta(any(), any(), any());
        verify(outboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
    }

    @Test void draftOutboundCannotSkipWorkflowAndConfirm() {
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(outbound(InventoryOrderStatusEnum.DRAFT));
        assertFalse(service.confirm(1L, 9L, "tester"));
        verify(stockMutationService, never()).applyDelta(any(), any(), any());
        verify(stockMutationService, never()).applyDelta(any(), any(), any());
    }

    @Test void insufficientStockDoesNotWriteTransactionOrTerminalStatus() {
        InventoryOutboundItem item = new InventoryOutboundItem();
        item.setMaterialId(11L);
        item.setInventoryItemId(31L);
        item.setMaterialCode("MAT-11");
        item.setQuantity(new BigDecimal("10"));
        InventoryStockItem batch = new InventoryStockItem();
        batch.setItemId(21L);
        batch.setQuantity(new BigDecimal("3"));
        batch.setReservedQuantity(BigDecimal.ZERO);
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(outbound(InventoryOrderStatusEnum.APPROVED));
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(item));
        // DEV-20260909-001 后 confirm 按库存物品身份（inventoryItemId）走 FIFO 扣减（非旧 selectFIFOAvailable(materialId)）
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(List.of(batch));
        assertThrows(BusinessException.class, () -> service.confirm(1L, 9L, "tester"));
        verify(stockMutationService).applyDelta(eq(batch), eq(new BigDecimal("-3")), any(InventoryTransaction.class));
        verify(stockMapper, never()).refreshSummary(any());
        verify(outboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
    }

    @Test void linkedDeliveryShipsOnlyAfterLedgerAndWarehouseCompletion() {
        InventoryOutboundOrder order=outbound(InventoryOrderStatusEnum.APPROVED);
        order.setSourceType(com.jjx.sales.service.SalesDeliveryWorkflowService.OUTBOUND_SOURCE);order.setSourceId(20L);
        InventoryOutboundItem item=new InventoryOutboundItem();item.setInventoryItemId(31L);item.setDeliveryItemId(30L);item.setQuantity(BigDecimal.valueOf(2));
        InventoryStockItem batch=new InventoryStockItem();batch.setItemId(21L);batch.setInventoryItemId(31L);batch.setQuantity(BigDecimal.valueOf(5));batch.setReservedQuantity(BigDecimal.ZERO);
        when(outboundOrderMapper.selectById(1L)).thenReturn(order);
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(item));
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(List.of(batch));
        when(outboundOrderMapper.updateById(any(InventoryOutboundOrder.class))).thenReturn(1);
        assertTrue(service.confirm(1L,9L,"tester"));
        var sequence=inOrder(deliveryWorkflow,stockMutationService,outboundOrderMapper);
        sequence.verify(deliveryWorkflow).prepareShipment(20L);
        sequence.verify(deliveryWorkflow).verifyOutbound(20L,List.of(item));
        sequence.verify(deliveryWorkflow).releaseOwnedReservations(List.of(item));
        sequence.verify(stockMutationService).applyDelta(eq(batch),eq(BigDecimal.valueOf(-2)),any(InventoryTransaction.class));
        sequence.verify(outboundOrderMapper).updateById(order);
        sequence.verify(deliveryWorkflow).completeShipment(20L);
        assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(),order.getOrderStatus());
    }

    private static InventoryOutboundOrder outbound(InventoryOrderStatusEnum status) {
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundId(1L);
        order.setOrderStatus(status.getValue());
        return order;
    }
}
