package com.jjx.inventory;

import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryOutboundItem;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.OutboundTypeEnum;
import com.jjx.inventory.enums.TransactionTypeEnum;
import com.jjx.inventory.mapper.InventoryOutboundItemMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不连接业务库；仓储替身模拟预占及 FIFO 筛选，数量扣减调用真实 applyDelta。 */
@ExtendWith(MockitoExtension.class)
class InventoryPickReservationLifecycleTest {
    private static final BigDecimal PICK_QUANTITY = new BigDecimal("5");

    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @Mock InventoryStockMutationService stockMutationService;
    @Mock InventoryAlertService alertService;
    @Mock EventPublisher eventPublisher;
    @InjectMocks InventoryOutboundServiceImpl service;

    private InventoryOutboundOrder order;
    private InventoryStockItem batch;

    @BeforeEach
    void initMetadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, InventoryOutboundItem.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, InventoryOutboundOrder.class);
    }

    @ParameterizedTest
    @CsvSource({
            "sample, PENDING, 5", "sample, APPROVED, 5",
            "sample, PENDING, 20", "sample, APPROVED, 20",
            "work_order, APPROVED, 5", "work_order, APPROVED, 20"
    })
    void confirmReleasesBeforeFifoAndDeductsOnlyOnce(String source, InventoryOrderStatusEnum status,
                                                   BigDecimal originalQuantity) {
        prepare(source, status, originalQuantity, PICK_QUANTITY);
        if ("sample".equals(source)) {
            when(outboundItemMapper.update(isNull(), any())).thenAnswer(invocation -> {
                var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) invocation.getArgument(1);
                assertUpdateValue(update, "quantity", PICK_QUANTITY);
                assertUpdateValue(update, "unit_price", new BigDecimal("3.50"));
                assertUpdateValue(update, "amount", new BigDecimal("17.50"));
                assertUpdateValue(update, "batch_no", "PICK-BATCH");
                assertTrue(update.getSqlSegment().contains("item_id"));
                assertTrue(update.getParamNameValuePairs().containsValue(81L));
                return 1;
            });
            when(outboundOrderMapper.update(isNull(), any())).thenAnswer(invocation -> {
                var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) invocation.getArgument(1);
                assertUpdateValue(update, "total_amount", new BigDecimal("17.50"));
                return 1;
            });
        }
        stubRelease();
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenAnswer(invocation ->
                available().signum() > 0 ? List.of(batch) : List.of());
        when(stockItemMapper.selectByIdForUpdate(21L)).thenReturn(batch);
        InventoryStockMutationServiceImpl mutation = new InventoryStockMutationServiceImpl(
                stockItemMapper, stockMapper, transactionMapper);
        when(stockMutationService.applyDelta(any(), any(), any())).thenAnswer(invocation ->
                mutation.applyDelta(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        BigDecimal beforeConfirmAvailable = available();

        assertTrue(service.confirm(1L, 9L, "tester"));

        assertEquals(originalQuantity.subtract(PICK_QUANTITY), batch.getQuantity());
        assertEquals(BigDecimal.ZERO, batch.getReservedQuantity());
        // 原可用量指建单预占前；确认时释放 Q 再实扣 Q，可用量不应再次减少 Q。
        assertEquals(originalQuantity.subtract(PICK_QUANTITY), available());
        assertEquals(beforeConfirmAvailable, available());
        assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(), order.getOrderStatus());
        var sequence = inOrder(stockItemMapper, stockMapper, stockMutationService);
        sequence.verify(stockItemMapper).releaseReserved(21L, PICK_QUANTITY);
        sequence.verify(stockMapper).refreshSummary(11L);
        sequence.verify(stockItemMapper).selectFIFOAvailableByInventoryItemId(31L);
        sequence.verify(stockMutationService).applyDelta(eq(batch), eq(PICK_QUANTITY.negate()), any());
        verify(stockMapper).refreshSummaryByInventoryItemId(31L);
        ArgumentCaptor<InventoryTransaction> tx = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(tx.capture());
        assertEquals(TransactionTypeEnum.OUTBOUND.getCode(), tx.getValue().getTransactionType());
        assertEquals(PICK_QUANTITY.negate(), tx.getValue().getQuantity());
        assertEquals(originalQuantity, tx.getValue().getBeforeQuantity());
        assertEquals(batch.getQuantity(), tx.getValue().getAfterQuantity());
        assertEquals(source, tx.getValue().getSourceType());
        if ("sample".equals(source)) {
            verify(outboundItemMapper).update(isNull(), any());
            verify(outboundOrderMapper).update(isNull(), any());
            assertEquals(new BigDecimal("17.50"), order.getTotalAmount());
        } else {
            verify(outboundItemMapper, never()).update(isNull(), any());
            verify(outboundOrderMapper, never()).update(isNull(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({"sample, PENDING", "sample, APPROVED", "work_order, PENDING", "work_order, APPROVED"})
    void cancelRestoresAvailabilityWithoutChangingQuantity(String source, InventoryOrderStatusEnum status) {
        prepare(source, status, new BigDecimal("20"), PICK_QUANTITY);
        stubRelease();
        BigDecimal beforeAvailable = available();

        assertTrue(service.cancel(1L, "不再领料"));

        assertReleasedWithoutDeduction(InventoryOrderStatusEnum.CANCELLED);
        assertEquals(beforeAvailable.add(PICK_QUANTITY), available());
        assertEquals("不再领料", order.getRemark());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sample", "work_order"})
    void rejectReleasesReservationWithoutDeductingStock(String source) {
        prepare(source, InventoryOrderStatusEnum.PENDING, new BigDecimal("20"), PICK_QUANTITY);
        stubRelease();

        assertTrue(service.reject(1L, 9L, "tester", "领料有误"));

        assertReleasedWithoutDeduction(InventoryOrderStatusEnum.REJECTED);
        assertEquals("领料有误", order.getRemark());
    }

    @ParameterizedTest
    @CsvSource({"sample, CANCELLED", "sample, REJECTED", "work_order, CANCELLED", "work_order, REJECTED"})
    void resubmitReservesAgainAfterCancelOrReject(String source, InventoryOrderStatusEnum status) {
        prepare(source, status, new BigDecimal("20"), BigDecimal.ZERO);
        when(stockItemMapper.selectFIFOAvailableForUpdate(11L)).thenReturn(List.of(batch));
        when(stockItemMapper.addReserved(21L, PICK_QUANTITY)).thenAnswer(invocation -> {
            batch.setReservedQuantity(batch.getReservedQuantity().add(invocation.getArgument(1)));
            return 1;
        });

        assertTrue(service.submitApprove(1L));

        assertEquals(PICK_QUANTITY, batch.getReservedQuantity());
        assertEquals(new BigDecimal("20"), batch.getQuantity());
        assertEquals(new BigDecimal("15"), available());
        assertEquals(InventoryOrderStatusEnum.PENDING.getValue(), order.getOrderStatus());
        verify(stockItemMapper).addReserved(21L, PICK_QUANTITY);
        verify(stockMapper).refreshSummary(11L);
        verifyNoInteractions(stockMutationService, transactionMapper);
    }

    @Test
    void cancellingOtherOutboundDoesNotReleasePickReservations() {
        order = new InventoryOutboundOrder();
        order.setOutboundId(1L);
        order.setSourceType("purchase");
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundOrderMapper.updateById(order)).thenReturn(1);

        assertTrue(service.cancel(1L, "取消退货"));

        verifyNoInteractions(outboundItemMapper, stockItemMapper, stockMapper, stockMutationService, transactionMapper);
    }

    private void prepare(String source, InventoryOrderStatusEnum status,
                         BigDecimal quantity, BigDecimal reserved) {
        order = new InventoryOutboundOrder();
        order.setOutboundId(1L);
        order.setSourceType(source);
        order.setOutboundType(("sample".equals(source) ? OutboundTypeEnum.SAMPLE : OutboundTypeEnum.PRODUCTION).getCode());
        order.setOrderStatus(status.getValue());
        InventoryOutboundItem item = new InventoryOutboundItem();
        item.setItemId(81L);
        item.setMaterialId(11L);
        item.setMaterialCode("MAT-11");
        item.setInventoryItemId(31L);
        item.setQuantity(PICK_QUANTITY);
        batch = new InventoryStockItem();
        batch.setItemId(21L);
        batch.setMaterialId(11L);
        batch.setInventoryItemId(31L);
        batch.setBatchNo("PICK-BATCH");
        batch.setUnitCost(new BigDecimal("3.50"));
        batch.setQuantity(quantity);
        batch.setReservedQuantity(reserved);
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundOrderMapper.updateById(order)).thenReturn(1);
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(List.of(item));
    }

    private void stubRelease() {
        when(stockItemMapper.selectFIFOReservedForUpdate(11L)).thenReturn(List.of(batch));
        when(stockItemMapper.releaseReserved(21L, PICK_QUANTITY)).thenAnswer(invocation -> {
            batch.setReservedQuantity(batch.getReservedQuantity().subtract(invocation.getArgument(1)));
            return 1;
        });
    }

    private void assertReleasedWithoutDeduction(InventoryOrderStatusEnum status) {
        assertEquals(new BigDecimal("20"), batch.getQuantity());
        assertEquals(BigDecimal.ZERO, batch.getReservedQuantity());
        assertEquals(new BigDecimal("20"), available());
        assertEquals(status.getValue(), order.getOrderStatus());
        verify(stockItemMapper).releaseReserved(21L, PICK_QUANTITY);
        verify(stockMapper).refreshSummary(11L);
        verifyNoInteractions(stockMutationService, transactionMapper);
    }

    private BigDecimal available() {
        return batch.getQuantity().subtract(batch.getReservedQuantity());
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
}
