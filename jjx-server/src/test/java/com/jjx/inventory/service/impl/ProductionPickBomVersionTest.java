package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.OutboundTypeEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionBomResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionPickBomVersionTest {
    @Mock ProductionOrderMapper productionOrderMapper;
    @Mock EngineeringBomMapper bomMapper;
    @Mock EngineeringBomItemMapper productBomItemMapper;
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @Mock InventoryWarehouseMapper outboundWarehouseMapper;
    @Mock InventoryStockMutationService stockMutationService;
    @Mock RedisSequenceService redisSequenceService;
    @InjectMocks InventoryOutboundServiceImpl service;
    ProductionOrder workOrder;
    EngineeringBom oldBom;
    EngineeringBomItem oldItem;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), EngineeringBomItem.class);
        workOrder = new ProductionOrder(); workOrder.setOrderId(20L); workOrder.setOrderType("WORK_ORDER");
        workOrder.setProductId(2L); workOrder.setBomId(5L); workOrder.setPlannedQuantity(BigDecimal.TEN);
        oldBom = new EngineeringBom(); oldBom.setBomId(5L); oldBom.setProductId(2L);
        oldBom.setIsCurrent(false); oldBom.setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue());
        oldItem = new EngineeringBomItem(); oldItem.setBomId(5L); oldItem.setMaterialId(31L);
        oldItem.setSourceType("buy"); oldItem.setActualIssueQty(new BigDecimal("2"));
        oldItem.setPositionNo("OLD-POSITION"); oldItem.setModuleQty(new BigDecimal("2"));
        when(productionOrderMapper.selectById(20L)).thenReturn(workOrder);
        ReflectionTestUtils.setField(service, "productionBomResolver", new ProductionBomResolver(bomMapper));
    }

    private void pinnedBom() {
        when(bomMapper.selectById(5L)).thenReturn(oldBom);
        when(productBomItemMapper.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<EngineeringBomItem> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("bom_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(5L));
            return List.of(oldItem);
        });
    }

    @Test void previewAndSupplementUsePinnedDemandAfterVersionSwitch() {
        pinnedBom();
        assertEquals(new BigDecimal("20"), service.previewPick(20L).get(0).get("demand"));
        assertEquals(new BigDecimal("6"), service.previewPick(20L, new BigDecimal("3")).get(0).get("demand"));
        verify(bomMapper, never()).selectOne(any());
    }

    @Test void remainingDemandUsesPinnedVersion() {
        pinnedBom();
        assertEquals(new BigDecimal("20"), service.getPickRemaining(20L).get(0).get("remaining"));
        verify(bomMapper, never()).selectOne(any());
    }

    @Test void generatedPickUsesPinnedMaterialsAndQuantity() {
        pinnedBom();
        InventoryWarehouse warehouse = new InventoryWarehouse(); warehouse.setWarehouseId(1L);
        when(outboundWarehouseMapper.selectOne(any())).thenReturn(warehouse);
        InventoryStockItem stock = new InventoryStockItem(); stock.setQuantity(new BigDecimal("100"));
        stock.setReservedQuantity(BigDecimal.ZERO);
        when(stockItemMapper.selectFIFOAvailable(31L)).thenReturn(List.of(stock));
        when(outboundOrderMapper.insert(any(InventoryOutboundOrder.class))).thenAnswer(call -> {
            InventoryOutboundOrder outbound = call.getArgument(0); outbound.setOutboundId(50L); return 1;
        });
        assertEquals(50L, service.createFromProduction(20L, null));
        ArgumentCaptor<InventoryOutboundItem> item = ArgumentCaptor.forClass(InventoryOutboundItem.class);
        verify(outboundItemMapper).insert(item.capture());
        assertEquals(31L, item.getValue().getMaterialId());
        assertEquals(new BigDecimal("20"), item.getValue().getQuantity());
        verify(bomMapper, never()).selectOne(any());
    }

    @Test void printUsesSamePinnedPositionAndModuleQuantity() {
        pinnedBom();
        when(outboundOrderMapper.selectById(50L)).thenReturn(outbound());
        InventoryOutboundItem item = new InventoryOutboundItem(); item.setMaterialId(31L);
        item.setQuantity(new BigDecimal("20"));
        when(outboundItemMapper.selectByOutboundId(50L)).thenReturn(List.of(item));
        var printed = service.getPickOrderPrint(50L).getItems().get(0);
        assertEquals("OLD-POSITION", printed.getProjectName());
        assertEquals(new BigDecimal("2"), printed.getModuleQty());
        assertEquals(new BigDecimal("20"), printed.getIssuedQuantity());
        verify(bomMapper, never()).selectOne(any());
    }

    @Test void brokenPinnedBomStopsWarehouseConfirmationBeforeStockChanges() {
        when(outboundOrderMapper.selectByIdForUpdate(50L)).thenReturn(outbound());
        assertThrows(BusinessException.class, () -> service.confirm(50L, 1L, "tester"));
        verifyNoInteractions(stockMutationService, stockItemMapper, outboundItemMapper);
        verify(outboundOrderMapper, never()).updateById(any(InventoryOutboundOrder.class));
        verify(bomMapper, never()).selectOne(any());
    }

    @Test void legacyPrintDoesNotPresentCurrentBomAsHistoricalFact() {
        workOrder.setBomId(null);
        when(outboundOrderMapper.selectById(50L)).thenReturn(outbound());
        InventoryOutboundItem item = new InventoryOutboundItem(); item.setMaterialId(31L);
        item.setQuantity(new BigDecimal("20"));
        when(outboundItemMapper.selectByOutboundId(50L)).thenReturn(List.of(item));
        var printed = service.getPickOrderPrint(50L).getItems().get(0);
        assertNull(printed.getProjectName());
        assertNull(printed.getModuleQty());
        assertEquals(new BigDecimal("20"), printed.getIssuedQuantity());
        verifyNoInteractions(bomMapper, productBomItemMapper);
    }

    private InventoryOutboundOrder outbound() {
        InventoryOutboundOrder outbound = new InventoryOutboundOrder(); outbound.setOutboundId(50L);
        outbound.setOutboundType(OutboundTypeEnum.PRODUCTION.getCode()); outbound.setSourceType("work_order");
        outbound.setSourceId(20L); outbound.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        return outbound;
    }
}
