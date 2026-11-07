package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.inventory.service.InventoryStockMutationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    @Mock com.jjx.production.service.ProductionBomResolver productionBomResolver;
    @InjectMocks InventoryOutboundServiceImpl service;

    @BeforeEach void initMetadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, InventoryOutboundItem.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, InventoryOutboundOrder.class);
    }

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

    @Test void pendingProductionPickCannotBeIssuedBeforeApproval() {
        InventoryOutboundOrder pendingPick = outbound(InventoryOrderStatusEnum.PENDING);
        pendingPick.setSourceType("work_order");
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(pendingPick);

        assertThrows(BusinessException.class, () -> service.confirm(1L, 9L, "tester"));
        verify(outboundItemMapper, never()).selectByOutboundId(any());
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

    @Test void productionPickStillAccumulatesItsExistingMaterialCost() {
        var order = outbound(InventoryOrderStatusEnum.APPROVED);
        order.setSourceType("work_order");
        order.setSourceId(20L);
        var production = new com.jjx.production.domain.entity.ProductionOrder();
        production.setMaterialStatus(2);
        production.setMaterialCost(new BigDecimal("10"));
        when(productionOrderMapper.selectById(20L)).thenReturn(production);
        var item = new InventoryOutboundItem();
        item.setInventoryItemId(31L);
        item.setMaterialId(11L);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("3.5"));
        var stock = new InventoryStockItem();
        stock.setQuantity(new BigDecimal("5"));
        stock.setReservedQuantity(BigDecimal.ZERO);
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(item));
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(List.of(stock));
        when(outboundOrderMapper.updateById(any(InventoryOutboundOrder.class))).thenReturn(1);

        assertTrue(service.confirm(1L, 9L, "tester"));

        assertEquals(0, new BigDecimal("17").compareTo(production.getMaterialCost()));
        verify(productionOrderMapper).updateById(production);
        verify(outboundItemMapper, never()).update(any(), any());
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
        verify(outboundOrderMapper, never()).update(any(), any());
        assertNull(order.getTotalAmount());
        verify(stockMutationService).applyDelta(eq(stock), eq(new BigDecimal("-2")),
                argThat(tx -> new BigDecimal("3.5").equals(tx.getUnitCost())));
    }

    @Test void sampleConfirmationReplacesEstimatedPriceWithActualBatchCost() {
        var order = sampleOrder();
        var item = sampleItem("2");
        var actualBatch = batch(41L, "ACTUAL", "5", "3.5", null);
        stubSampleConfirmation(order, item);
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(List.of(actualBatch));
        when(outboundItemMapper.update(isNull(), any())).thenAnswer(inv -> {
            var update = inv.<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InventoryOutboundItem>>getArgument(1);
            assertUpdateValue(update, "unit_price", new BigDecimal("3.5"));
            assertUpdateValue(update, "amount", new BigDecimal("7.00"));
            assertUpdateValue(update, "batch_no", "ACTUAL");
            return 1;
        });

        assertTrue(service.confirm(1L, 9L, "tester"));

        assertEquals(new BigDecimal("7.00"), order.getTotalAmount());
        verify(stockMutationService).applyDelta(eq(actualBatch), eq(new BigDecimal("-2")),
                argThat(tx -> new BigDecimal("3.5").equals(tx.getUnitCost())));
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
        verifyNoInteractions(productionOrderMapper);
    }

    @Test void sampleConfirmationClearsStalePriceWhenActualBatchCostIsMissing() {
        var order = sampleOrder();
        stubSampleConfirmation(order, sampleItem("2"));
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(
                List.of(batch(41L, "NO-COST", "5", null, null)));
        when(outboundItemMapper.update(isNull(), any())).thenAnswer(inv -> {
            var update = inv.<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InventoryOutboundItem>>getArgument(1);
            assertUpdateValue(update, "unit_price", null);
            assertUpdateValue(update, "amount", null);
            return 1;
        });
        when(outboundOrderMapper.update(isNull(), any())).thenAnswer(inv -> {
            var update = inv.<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InventoryOutboundOrder>>getArgument(1);
            assertUpdateValue(update, "total_amount", null);
            return 1;
        });

        assertTrue(service.confirm(1L, 9L, "tester"));

        assertNull(order.getTotalAmount());
        verify(stockMutationService).applyDelta(any(), eq(new BigDecimal("-2")),
                argThat(tx -> tx.getUnitCost() == null));
    }

    @Test void sampleConfirmationSplitsActualLocationAndGlobalBatchesWithoutChangingTotalQuantity() {
        var order = sampleOrder();
        var item = sampleItem("3");
        item.setLocationId(9L);
        stubSampleConfirmation(order, item);
        var local = batch(41L, "LOCAL", "1", "3.5", 9L);
        var global = batch(42L, "GLOBAL", "2", "5", 10L);
        when(stockItemMapper.selectFIFOAvailableByInventoryItemAndLocation(31L, 9L)).thenReturn(List.of(local));
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(List.of(global));
        when(outboundItemMapper.update(isNull(), any())).thenAnswer(inv -> {
            var update = inv.<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InventoryOutboundItem>>getArgument(1);
            assertUpdateValue(update, "quantity", BigDecimal.ONE);
            assertUpdateValue(update, "amount", new BigDecimal("3.50"));
            assertUpdateValue(update, "batch_no", "LOCAL");
            return 1;
        });
        when(outboundItemMapper.insert(any(InventoryOutboundItem.class))).thenReturn(1);

        assertTrue(service.confirm(1L, 9L, "tester"));

        var inserted = org.mockito.ArgumentCaptor.forClass(InventoryOutboundItem.class);
        verify(outboundItemMapper).insert(inserted.capture());
        var extra = inserted.getValue();
        assertNull(extra.getItemId());
        assertEquals(new BigDecimal("2"), extra.getQuantity());
        assertEquals(new BigDecimal("10.00"), extra.getAmount());
        assertEquals("GLOBAL", extra.getBatchNo());
        assertEquals(10L, extra.getLocationId());
        assertEquals("[sampleRound=2]", extra.getRemark());
        assertEquals(new BigDecimal("13.50"), order.getTotalAmount());
        assertEquals(new BigDecimal("3"), item.getQuantity());
        verify(stockMutationService).applyDelta(eq(local), eq(BigDecimal.ONE.negate()),
                argThat(tx -> new BigDecimal("3.5").equals(tx.getUnitCost())));
        verify(stockMutationService).applyDelta(eq(global), eq(new BigDecimal("-2")),
                argThat(tx -> new BigDecimal("5").equals(tx.getUnitCost())));
    }

    @Test void sampleAmountWriteFailureAbortsBeforeCompletedStatus() {
        var order = sampleOrder();
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(sampleItem("2")));
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(
                List.of(batch(41L, "A", "5", "3.5", null)));
        when(outboundItemMapper.update(isNull(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.confirm(1L, 9L, "tester"));

        assertEquals(InventoryOrderStatusEnum.PENDING.getValue(), order.getOrderStatus());
        verify(outboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
    }

    private void stubSampleConfirmation(InventoryOutboundOrder order, InventoryOutboundItem item) {
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(item));
        when(outboundOrderMapper.updateById(any(InventoryOutboundOrder.class))).thenReturn(1);
        when(outboundOrderMapper.update(isNull(), any())).thenReturn(1);
    }

    private InventoryOutboundOrder sampleOrder() {
        var order = outbound(InventoryOrderStatusEnum.PENDING);
        order.setSourceType("sample");
        order.setSourceId(7L);
        order.setTotalAmount(new BigDecimal("99"));
        return order;
    }

    private InventoryOutboundItem sampleItem(String quantity) {
        var item = new InventoryOutboundItem();
        item.setItemId(11L);
        item.setOutboundId(1L);
        item.setMaterialId(21L);
        item.setInventoryItemId(31L);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal("99"));
        item.setAmount(new BigDecimal("198"));
        item.setBatchNo("ESTIMATED");
        item.setRemark("[sampleRound=2]");
        return item;
    }

    private InventoryStockItem batch(Long id, String batchNo, String quantity, String cost, Long location) {
        var stock = new InventoryStockItem();
        stock.setItemId(id);
        stock.setQuantity(new BigDecimal(quantity));
        stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setUnitCost(cost == null ? null : new BigDecimal(cost));
        stock.setBatchNo(batchNo);
        stock.setLocationId(location);
        return stock;
    }

    private void assertUpdateValue(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> update,
            String column, Object expected) {
        String assignment = java.util.Arrays.stream(update.getSqlSet().split(","))
                .filter(part -> part.startsWith(column + "=")).findFirst().orElseThrow();
        var parameter = java.util.regex.Pattern.compile("ew\\.paramNameValuePairs\\.([A-Za-z0-9]+)")
                .matcher(assignment);
        assertTrue(parameter.find(), assignment);
        assertEquals(expected, update.getParamNameValuePairs().get(parameter.group(1)), column);
    }

    private static InventoryOutboundOrder outbound(InventoryOrderStatusEnum status) {
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundId(1L);
        order.setOrderStatus(status.getValue());
        return order;
    }
}
