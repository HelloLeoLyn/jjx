package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.sales.domain.entity.SalesSampleBom;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SamplePickAmountTest {
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @Mock OrderMapper salesOrderMapper;
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryMaterialMapper materialMapper;
    @Mock InventoryWarehouseMapper outboundWarehouseMapper;
    @Mock RedisSequenceService redisSequenceService;
    @InjectMocks InventoryOutboundServiceImpl service;
    private final List<InventoryOutboundItem> saved = new ArrayList<>();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                InventoryOutboundOrder.class);
        var sample = new SalesSampleOrder();
        sample.setSampleQty(1);
        sample.setSampleRound(2);
        when(sampleOrderMapper.selectByOrderId(7L)).thenReturn(sample);
        var bom = new SalesSampleBom();
        bom.setMaterialId(11L);
        bom.setMaterialName("材料");
        bom.setQuantity(new BigDecimal("10"));
        when(sampleBomMapper.selectList(any())).thenReturn(List.of(bom));
        var material = new InventoryMaterial(); material.setMaterialId(11L);
        when(materialMapper.selectById(11L)).thenReturn(material);
        var warehouse = new InventoryWarehouse(); warehouse.setWarehouseId(1L);
        when(outboundWarehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(redisSequenceService.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("SPK-7");
        when(outboundOrderMapper.insert(any(InventoryOutboundOrder.class))).thenAnswer(invocation -> {
            invocation.<InventoryOutboundOrder>getArgument(0).setOutboundId(100L);
            return 1;
        });
        when(outboundItemMapper.insert(any(InventoryOutboundItem.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return 1;
        });
        when(outboundItemMapper.selectByOutboundId(100L)).thenReturn(saved);
    }

    @Test void twoUnitsAtBatchCostThreePointFiveProduceSevenAndKeepReservationQuantity() {
        var batch = batch(1L, "A", "5", "3.5", 9L);
        when(stockItemMapper.selectFIFOAvailableForUpdate(11L)).thenReturn(List.of(batch));

        service.createSamplePick(7L, List.of(request("2")));

        assertEquals(1, saved.size());
        assertEquals(new BigDecimal("3.5"), saved.getFirst().getUnitPrice());
        assertEquals(new BigDecimal("7.00"), saved.getFirst().getAmount());
        assertEquals("A", saved.getFirst().getBatchNo());
        assertEquals("[sampleRound=2]", saved.getFirst().getRemark());
        assertEquals(new BigDecimal("7.00"), header().getTotalAmount());
        assertEquals(new BigDecimal("2"), header().getTotalQuantity());
        verify(stockItemMapper).addReserved(1L, new BigDecimal("2"));
        verify(stockItemMapper, never()).updateById(any(InventoryStockItem.class));
    }

    @Test void unknownBatchCostLeavesPriceAmountAndHeaderNullRatherThanZero() {
        when(stockItemMapper.selectFIFOAvailableForUpdate(11L)).thenReturn(List.of(batch(1L, "A", "5", null, 9L)));

        service.createSamplePick(7L, List.of(request("2")));

        assertNull(saved.getFirst().getUnitPrice());
        assertNull(saved.getFirst().getAmount());
        assertNull(header().getTotalAmount());
        // 显式清掉单头默认的 0.00，不能被 NOT_NULL 策略跳过。
        verify(outboundOrderMapper).update(isNull(), argThat(wrapper -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InventoryOutboundOrder>) wrapper;
            assertTrue(update.getSqlSet().contains("total_amount="));
            return update.getParamNameValuePairs().containsValue(null);
        }));
    }

    @Test void differentBatchPricesAreSplitAndRecommendedLocationOrderIsPreserved() {
        var first = batch(1L, "A", "1", "3.5", 9L);
        var second = batch(2L, "B", "2", "5", 10L);
        var sameLocation = batch(3L, "C", "1", "4", 9L);
        when(stockItemMapper.selectFIFOAvailableForUpdate(11L)).thenReturn(List.of(first, second, sameLocation));

        service.createSamplePick(7L, List.of(request("3")));

        assertEquals(List.of("A", "C", "B"), saved.stream().map(InventoryOutboundItem::getBatchNo).toList());
        assertEquals(List.of(new BigDecimal("3.50"), new BigDecimal("4.00"), new BigDecimal("5.00")),
                saved.stream().map(InventoryOutboundItem::getAmount).toList());
        assertEquals(new BigDecimal("12.50"), header().getTotalAmount());
        assertEquals(new BigDecimal("3"), header().getTotalQuantity());
        assertTrue(saved.stream().allMatch(item -> Long.valueOf(9L).equals(item.getLocationId())));
    }

    @Test void repeatedMaterialRequestsCannotPriceTheSameBatchQuantityTwice() {
        when(stockItemMapper.selectFIFOAvailableForUpdate(11L)).thenReturn(
                List.of(batch(1L, "A", "2", "3.5", null), batch(2L, "B", "3", "5", null)));

        service.createSamplePick(7L, List.of(request("2"), request("1")));

        assertEquals(List.of("A", "B"), saved.stream().map(InventoryOutboundItem::getBatchNo).toList());
        assertEquals(new BigDecimal("12.00"), header().getTotalAmount());
        assertEquals(new BigDecimal("3"), header().getTotalQuantity());
    }

    @Test void knownZeroBatchCostIsDifferentFromMissingCost() {
        when(stockItemMapper.selectFIFOAvailableForUpdate(11L)).thenReturn(List.of(batch(1L, "A", "2", "0", null)));
        service.createSamplePick(7L, List.of(request("2")));
        assertEquals(BigDecimal.ZERO, saved.getFirst().getUnitPrice());
        assertEquals(new BigDecimal("0.00"), saved.getFirst().getAmount());
        assertEquals(new BigDecimal("0.00"), header().getTotalAmount());
    }

    private InventoryOutboundOrder header() {
        var header = ArgumentCaptor.forClass(InventoryOutboundOrder.class);
        verify(outboundOrderMapper).updateById(header.capture());
        return header.getValue();
    }

    private InventoryStockItem batch(Long id, String batchNo, String qty, String price, Long location) {
        var batch = new InventoryStockItem();
        batch.setItemId(id);
        batch.setBatchNo(batchNo);
        batch.setQuantity(new BigDecimal(qty));
        batch.setReservedQuantity(BigDecimal.ZERO);
        batch.setUnitCost(price == null ? null : new BigDecimal(price));
        batch.setLocationId(location);
        return batch;
    }

    private Map<String, Object> request(String quantity) {
        return Map.of("materialId", 11L, "materialName", "材料", "quantity", quantity);
    }
}
