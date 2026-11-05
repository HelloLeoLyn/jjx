package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.InventoryOutboundItem;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryOutboundItemMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesSampleBom;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 订单ID与样品扩展主键刻意不相等；仅使用仓储替身，不写业务库。 */
@ExtendWith(MockitoExtension.class)
class InventorySamplePickOrderIdTest {
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @Mock OrderMapper salesOrderMapper;
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryMaterialMapper materialMapper;
    @Mock InventoryWarehouseMapper outboundWarehouseMapper;
    @Mock RedisSequenceService redisSequenceService;
    @InjectMocks InventoryOutboundServiceImpl service;

    @Test
    void previewFindsSampleWithDifferentExtensionIdAndKeepsIssuedQuantity() {
        when(sampleOrderMapper.selectByOrderId(2L)).thenReturn(sample());
        SalesSampleBom bom = new SalesSampleBom();
        bom.setOrderId(2L);
        bom.setMaterialName("测试材料");
        bom.setQuantity(new BigDecimal("5"));
        when(sampleBomMapper.selectList(any())).thenReturn(List.of(bom));
        InventoryOutboundOrder issued = new InventoryOutboundOrder();
        issued.setOutboundId(100L);
        issued.setSourceId(2L);
        when(outboundOrderMapper.selectList(any())).thenReturn(List.of(issued));
        InventoryOutboundItem issuedItem = new InventoryOutboundItem();
        issuedItem.setMaterialName("测试材料");
        issuedItem.setQuantity(new BigDecimal("2"));
        when(outboundItemMapper.selectByOutboundId(100L)).thenReturn(List.of(issuedItem));

        List<Map<String, Object>> rows = service.previewSamplePick(2L);

        assertEquals(1, rows.size());
        assertEquals("测试材料", rows.get(0).get("materialName"));
        assertEquals(new BigDecimal("5"), rows.get(0).get("quantity"));
        assertEquals(new BigDecimal("2"), rows.get(0).get("issuedQuantity"));
        assertNull(rows.get(0).get("materialId"));
        verify(outboundOrderMapper, never()).insert(any(InventoryOutboundOrder.class));
    }

    @Test
    void createLinksOutboundToOrderInsteadOfExtensionId() {
        when(sampleOrderMapper.selectByOrderId(2L)).thenReturn(sample());
        SalesOrder source = new SalesOrder();
        source.setOrderId(2L);
        source.setOrderNo("SP261102001");
        when(salesOrderMapper.selectById(2L)).thenReturn(source);
        InventoryWarehouse warehouse = new InventoryWarehouse();
        warehouse.setWarehouseId(7L);
        when(outboundWarehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(redisSequenceService.generateBusinessNumberByType("outbound_sample_pick", "SPK", "yyMMdd", 3))
                .thenReturn("SPK261105001");
        when(outboundOrderMapper.insert(any(InventoryOutboundOrder.class))).thenAnswer(invocation -> {
            invocation.<InventoryOutboundOrder>getArgument(0).setOutboundId(100L);
            return 1;
        });

        assertEquals(100L, service.createSamplePick(2L, pickItems()));

        ArgumentCaptor<InventoryOutboundOrder> order = ArgumentCaptor.forClass(InventoryOutboundOrder.class);
        verify(outboundOrderMapper).insert(order.capture());
        assertEquals(2L, order.getValue().getSourceId());
        assertEquals("sample", order.getValue().getSourceType());
        assertEquals("SP261102001", order.getValue().getSourceNo());
        assertEquals(new BigDecimal("3"), order.getValue().getTotalQuantity());
        ArgumentCaptor<InventoryOutboundItem> item = ArgumentCaptor.forClass(InventoryOutboundItem.class);
        verify(outboundItemMapper).insert(item.capture());
        assertEquals(100L, item.getValue().getOutboundId());
        assertEquals(11L, item.getValue().getMaterialId());
        assertEquals(new BigDecimal("3"), item.getValue().getQuantity());
    }

    @Test
    void previewRejectsMissingSampleWithoutReadingBomOrWritingOutbound() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.previewSamplePick(99L));

        assertEquals("样品单不存在: 99", error.getMessage());
        verifyNoInteractions(sampleBomMapper, outboundOrderMapper, outboundItemMapper, stockItemMapper);
    }

    @Test
    void createRejectsMissingSampleBeforeGeneratingNumberOrWritingOutbound() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createSamplePick(99L, pickItems()));

        assertEquals("样品单不存在: 99", error.getMessage());
        verifyNoInteractions(redisSequenceService, salesOrderMapper, outboundOrderMapper,
                outboundItemMapper, stockItemMapper, outboundWarehouseMapper);
    }

    private SalesSampleOrder sample() {
        SalesSampleOrder sample = new SalesSampleOrder();
        sample.setSampleOrderId(1L);
        sample.setOrderId(2L);
        return sample;
    }

    private List<Map<String, Object>> pickItems() {
        return List.of(Map.of("materialId", 11L, "materialCode", "MAT-11",
                "materialName", "测试材料", "quantity", 3));
    }
}
