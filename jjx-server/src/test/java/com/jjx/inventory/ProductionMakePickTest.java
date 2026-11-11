package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.TransactionTypeEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.inventory.service.impl.InventoryOutboundServiceImpl;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionBomResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仓储替身验证领料入口和预占；确认调用真实 applyDelta，不代表真实库过账验收。 */
@ExtendWith(MockitoExtension.class)
class ProductionMakePickTest {
    @Mock InventoryOutboundOrderMapper outboundOrderMapper;
    @Mock InventoryOutboundItemMapper outboundItemMapper;
    @Mock InventoryStockItemMapper stockItemMapper;
    @Mock InventoryStockMapper stockMapper;
    @Mock InventoryTransactionMapper transactionMapper;
    @Mock InventoryWarehouseMapper outboundWarehouseMapper;
    @Mock ProductionOrderMapper productionOrderMapper;
    @Mock ProductionBomResolver productionBomResolver;
    @Mock EngineeringBomItemMapper productBomItemMapper;
    @Mock RedisSequenceService redisSequenceService;
    @Mock InventoryStockMutationService stockMutationService;
    @Mock EventPublisher eventPublisher;
    @InjectMocks InventoryOutboundServiceImpl service;

    ProductionOrder production;
    EngineeringBomItem bomItem;

    @BeforeEach void setup() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(InventoryOutboundOrder.class, InventoryOutboundItem.class,
                EngineeringBomItem.class, InventoryWarehouse.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        production = new ProductionOrder();
        production.setOrderId(20L);
        production.setOrderType("WORK_ORDER");
        production.setOrderNo("TEST-MAKE");
        production.setPlannedQuantity(BigDecimal.ONE);
        when(productionOrderMapper.selectById(20L)).thenReturn(production);
        var bom = new EngineeringBom();
        bom.setBomId(4L);
        when(productionBomResolver.resolve(production)).thenReturn(bom);
        bomItem = new EngineeringBomItem();
        bomItem.setItemId(13L);
        bomItem.setMaterialId(349L);
        bomItem.setMaterialCode("SFG000345");
        bomItem.setSourceType("make");
        bomItem.setActualIssueQty(BigDecimal.ONE);
        when(productBomItemMapper.selectList(any())).thenReturn(List.of(bomItem));
    }

    @ParameterizedTest @ValueSource(strings = {"make", "buy"})
    void previewAndSupplementIncludeStockedMaterial(String source) {
        bomItem.setSourceType(source);
        when(stockItemMapper.selectFIFOAvailable(349L)).thenReturn(List.of(batch("10")));
        var preview = service.previewPick(20L);
        assertEquals(1, preview.size());
        assertEquals(349L, preview.get(0).get("materialId"));
        assertEquals(BigDecimal.ONE, preview.get(0).get("qtyPick"));
        var supplement = service.previewPick(20L, new BigDecimal("2"));
        assertEquals(new BigDecimal("2"), supplement.get(0).get("qtyPick"));
        assertEquals(BigDecimal.ONE, service.getPickRemaining(20L).get(0).get("remaining"));
    }

    @Test void linkedMakeIsExcludedBeforeAnyOrderOrStockWrite() {
        bomItem.setSubBomId(99L);
        var previewError = assertThrows(BusinessException.class, () -> service.previewPick(20L));
        var createError = assertThrows(BusinessException.class, () -> service.createFromProduction(20L, null));
        assertTrue(previewError.getMessage().contains("下层 BOM"));
        assertFalse(createError.getMessage().contains("库存不足"));
        verifyNoInteractions(stockItemMapper, redisSequenceService);
        verify(outboundOrderMapper, never()).insert(any(InventoryOutboundOrder.class));
    }

    @Test void missingMaterialIdentityIsExcluded() {
        bomItem.setMaterialId(null);
        assertThrows(BusinessException.class, () -> service.previewPick(20L));
        verifyNoInteractions(stockItemMapper);
    }

    @Test void mixedBomStillPicksBuyAndLeafMakeButNeverLinkedMake() {
        var linked = new EngineeringBomItem();
        linked.setMaterialId(400L);
        linked.setSourceType("make");
        linked.setSubBomId(99L);
        var buy = new EngineeringBomItem();
        buy.setItemId(14L);
        buy.setMaterialId(350L);
        buy.setSourceType("buy");
        buy.setActualIssueQty(BigDecimal.ONE);
        when(productBomItemMapper.selectList(any())).thenReturn(List.of(bomItem, linked, buy));
        when(stockItemMapper.selectFIFOAvailable(349L)).thenReturn(List.of(batch("10")));
        when(stockItemMapper.selectFIFOAvailable(350L)).thenReturn(List.of(batch("10")));
        var preview = service.previewPick(20L);
        assertEquals(List.of(349L, 350L), preview.stream().map(row -> row.get("materialId")).toList());
        verify(stockItemMapper, never()).selectFIFOAvailable(400L);
    }

    @Test void zeroStockRemainsAShortage() {
        stubCreation();
        when(stockItemMapper.selectFIFOAvailable(349L)).thenReturn(List.of());
        assertEquals(BigDecimal.ZERO, service.previewPick(20L).get(0).get("qtyPick"));
        assertTrue(assertThrows(BusinessException.class, () -> service.createFromProduction(20L, null))
                .getMessage().contains("库存不足"));
        verify(outboundItemMapper, never()).insert(any(InventoryOutboundItem.class));
    }

    @ParameterizedTest @ValueSource(strings = {"make", "buy"})
    void generateReserveAndConfirmTenToNineWithOutboundLedger(String source) {
        bomItem.setSourceType(source);
        stubCreation();
        var batch = batch("10");
        List<InventoryOutboundItem> items = new ArrayList<>();
        when(stockItemMapper.selectFIFOAvailable(349L)).thenReturn(List.of(batch));
        when(outboundItemMapper.insert(any(InventoryOutboundItem.class))).thenAnswer(inv -> {
            InventoryOutboundItem item = inv.getArgument(0);
            item.setItemId(81L);
            item.setInventoryItemId(31L);
            items.add(item);
            return 1;
        });
        when(outboundItemMapper.selectByOutboundId(1L)).thenReturn(items);
        when(stockItemMapper.selectFIFOAvailableForUpdate(349L)).thenReturn(List.of(batch));
        when(stockItemMapper.addReserved(eq(21L), any())).thenAnswer(inv -> {
            batch.setReservedQuantity(batch.getReservedQuantity().add(inv.getArgument(1)));
            return 1;
        });
        assertEquals(1L, service.createFromProduction(20L, null));
        assertEquals(349L, items.get(0).getMaterialId());
        assertEquals(BigDecimal.ONE, items.get(0).getQuantity());
        assertEquals(BigDecimal.ONE, batch.getReservedQuantity());
        var captor = ArgumentCaptor.forClass(InventoryOutboundOrder.class);
        verify(outboundOrderMapper).insert(captor.capture());
        var order = captor.getValue();
        order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        when(outboundOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(outboundOrderMapper.updateById(any(InventoryOutboundOrder.class))).thenReturn(1);
        when(stockItemMapper.selectFIFOReservedForUpdate(349L)).thenReturn(List.of(batch));
        when(stockItemMapper.releaseReserved(eq(21L), any())).thenAnswer(inv -> {
            batch.setReservedQuantity(batch.getReservedQuantity().subtract(inv.getArgument(1)));
            return 1;
        });
        when(stockItemMapper.selectFIFOAvailableByInventoryItemId(31L)).thenReturn(List.of(batch));
        when(stockItemMapper.selectByIdForUpdate(21L)).thenReturn(batch);
        var mutation = new InventoryStockMutationServiceImpl(stockItemMapper, stockMapper, transactionMapper);
        when(stockMutationService.applyDelta(any(), any(), any())).thenAnswer(inv ->
                mutation.applyDelta(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        assertTrue(service.confirm(1L, 9L, "tester"));
        assertEquals(new BigDecimal("9"), batch.getQuantity());
        assertEquals(BigDecimal.ZERO, batch.getReservedQuantity());
        var tx = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionMapper).insert(tx.capture());
        assertEquals(TransactionTypeEnum.OUTBOUND.getCode(), tx.getValue().getTransactionType());
        assertEquals(BigDecimal.ONE.negate(), tx.getValue().getQuantity());
        assertEquals(349L, tx.getValue().getMaterialId());
        assertEquals(21L, tx.getValue().getStockItemId());
        assertEquals(81L, tx.getValue().getSourceItemId());
    }

    private void stubCreation() {
        var warehouse = new InventoryWarehouse();
        warehouse.setWarehouseId(1L);
        when(outboundWarehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(redisSequenceService.generateBusinessNumberByType("outbound_pick", "PICK", "yyMMdd", 3))
                .thenReturn("PICK-TEST");
        when(outboundOrderMapper.insert(any(InventoryOutboundOrder.class))).thenAnswer(inv -> {
            inv.<InventoryOutboundOrder>getArgument(0).setOutboundId(1L);
            return 1;
        });
    }

    private InventoryStockItem batch(String quantity) {
        var batch = new InventoryStockItem();
        batch.setItemId(21L);
        batch.setInventoryItemId(31L);
        batch.setMaterialId(349L);
        batch.setBatchNo("SEMI-TEST");
        batch.setQuantity(new BigDecimal(quantity));
        batch.setReservedQuantity(BigDecimal.ZERO);
        return batch;
    }
}
