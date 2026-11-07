package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryOutboundItem;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.InventoryOutboundItemMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 只用仓储替身，不连接或修改业务库。 */
@ExtendWith(MockitoExtension.class)
class SamplePickCostTest {
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock OrderMapper orderMapper;
    @Mock InventoryOutboundOrderMapper sampleOutboundOrderMapper;
    @Mock InventoryOutboundItemMapper sampleOutboundItemMapper;
    @InjectMocks SampleOrderServiceImpl service;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                InventoryOutboundOrder.class);
        when(sampleOrderMapper.selectByOrderId(7L)).thenReturn(new SalesSampleOrder());
    }

    @Test void readSummaryAddsMultipleDocumentsAndPreservesBatchDetailsWithoutWritingCost() {
        var first = order(100L, 1);
        var second = order(101L, 2);
        when(sampleOutboundOrderMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<InventoryOutboundOrder> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("source_type ="));
            assertTrue(sql.contains("source_id ="));
            assertTrue(sql.contains("order_status ="));
            assertTrue(query.getParamNameValuePairs().containsValue("sample"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            assertTrue(query.getParamNameValuePairs().containsValue(InventoryOrderStatusEnum.COMPLETED.getValue()));
            return List.of(first, second);
        });
        when(sampleOutboundItemMapper.selectByOutboundId(100L)).thenReturn(List.of(item("A", "2", "3.5")));
        when(sampleOutboundItemMapper.selectByOutboundId(101L)).thenReturn(List.of(item("B", "3", "4")));

        var summary = service.getPickCostSummary(7L, null);

        assertEquals(0, new BigDecimal("19").compareTo(summary.totalAmount()));
        assertEquals(summary.totalAmount(), summary.knownAmount());
        assertEquals(2, summary.items().size());
        assertEquals("A", summary.items().getFirst().batchNo());
        assertEquals(new BigDecimal("7.0"), summary.items().getFirst().amount());
        verifyNoInteractions(orderMapper);
        verify(sampleOutboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
    }

    @Test void roundFilterUsesPersistedRoundAndDoesNotGuessHistoricalRound() {
        when(sampleOutboundOrderMapper.selectList(any())).thenReturn(
                List.of(order(100L, 1), order(101L, 2), order(102L, null)));
        when(sampleOutboundItemMapper.selectByOutboundId(100L)).thenReturn(List.of(item("A", "1", "1")));
        when(sampleOutboundItemMapper.selectByOutboundId(101L)).thenReturn(List.of(item("B", "2", "3.5")));
        when(sampleOutboundItemMapper.selectByOutboundId(102L)).thenReturn(List.of(item("C", "1", "1")));

        var summary = service.getPickCostSummary(7L, 2);

        assertEquals(new BigDecimal("7.0"), summary.totalAmount());
        assertEquals(1, summary.unknownRoundOrderCount());
        assertEquals(1, summary.items().size());
    }

    @Test void missingCostStaysNullAndBlocksManualCostOverwrite() {
        when(orderMapper.selectByIdForUpdate(7L)).thenReturn(new SalesOrder());
        when(sampleOutboundOrderMapper.selectList(any())).thenReturn(List.of(order(100L, 1)));
        when(sampleOutboundItemMapper.selectByOutboundId(100L)).thenReturn(
                List.of(item("A", "2", "3.5"), item("B", "1", null)));

        var summary = service.getPickCostSummary(7L, null);

        assertNull(summary.totalAmount());
        assertNull(summary.items().get(1).amount());
        assertNull(summary.items().get(1).unitPrice());
        assertEquals(new BigDecimal("7.0"), summary.knownAmount());
        assertEquals(1, summary.missingCostCount());
        assertThrows(BusinessException.class, () -> service.recalculatePickCost(7L, null));
        verify(orderMapper, never()).updateById(any(SalesOrder.class));
    }

    @Test void explicitRecalculationWritesOnlySampleCostAndNeverWorkHours() {
        when(orderMapper.selectByIdForUpdate(7L)).thenReturn(new SalesOrder());
        when(sampleOutboundOrderMapper.selectList(any())).thenReturn(List.of(order(100L, 1)));
        when(sampleOutboundItemMapper.selectByOutboundId(100L)).thenReturn(List.of(item("A", "2", "3.5")));
        when(orderMapper.updateById(any(SalesOrder.class))).thenReturn(1);

        var summary = service.recalculatePickCost(7L, null);

        var update = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderMapper).updateById(update.capture());
        assertEquals(7L, update.getValue().getOrderId());
        assertEquals(new BigDecimal("7.0"), update.getValue().getSampleCost());
        assertNull(update.getValue().getSampleWorkHours());
        assertEquals(summary.totalAmount(), update.getValue().getSampleCost());
    }

    @Test void noIssuedItemsCannotReplaceManualCostWithZero() {
        when(orderMapper.selectByIdForUpdate(7L)).thenReturn(new SalesOrder());
        when(sampleOutboundOrderMapper.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.recalculatePickCost(7L, null));
        verify(orderMapper, never()).updateById(any(SalesOrder.class));
    }

    @Test void roundWithUnassignedHistoricalOrdersCannotSilentlyUnderstateCost() {
        when(orderMapper.selectByIdForUpdate(7L)).thenReturn(new SalesOrder());
        when(sampleOutboundOrderMapper.selectList(any())).thenReturn(List.of(order(100L, 1), order(102L, null)));
        when(sampleOutboundItemMapper.selectByOutboundId(100L)).thenReturn(List.of(item("A", "2", "3.5")));
        when(sampleOutboundItemMapper.selectByOutboundId(102L)).thenReturn(List.of(item("B", "2", "3.5")));
        assertThrows(BusinessException.class, () -> service.recalculatePickCost(7L, 1));
        verify(orderMapper, never()).updateById(any(SalesOrder.class));
    }

    @Test void invalidRoundIsRejectedBeforeQueryingInventory() {
        assertThrows(BusinessException.class, () -> service.getPickCostSummary(7L, 0));
        verifyNoInteractions(sampleOutboundOrderMapper, sampleOutboundItemMapper);
    }

    @Test void itemRoundSurvivesHeaderRemarkChangesDuringRejectAndResubmit() {
        var order = order(100L, null);
        order.setRemark("曾驳回，现已重新发料");
        var item = item("A", "2", "3.5");
        item.setRemark("[sampleRound=2]");
        when(sampleOutboundOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(sampleOutboundItemMapper.selectByOutboundId(100L)).thenReturn(List.of(item));

        var summary = service.getPickCostSummary(7L, 2);

        assertEquals(new BigDecimal("7.0"), summary.totalAmount());
        assertEquals(0, summary.unknownRoundOrderCount());
    }

    private InventoryOutboundOrder order(Long id, Integer round) {
        var order = new InventoryOutboundOrder();
        order.setOutboundId(id);
        order.setOutboundNo("SPK-" + id);
        if (round != null) order.setRemark("[sampleRound=" + round + "]");
        return order;
    }

    private InventoryOutboundItem item(String batch, String quantity, String price) {
        var item = new InventoryOutboundItem();
        item.setMaterialId(11L);
        item.setMaterialCode("M11");
        item.setMaterialName("材料");
        item.setBatchNo(batch);
        item.setQuantity(new BigDecimal(quantity));
        if (price != null) {
            item.setUnitPrice(new BigDecimal(price));
            item.setAmount(item.getQuantity().multiply(item.getUnitPrice()));
        }
        return item;
    }
}
