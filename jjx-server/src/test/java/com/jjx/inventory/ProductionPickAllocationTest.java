package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryRollService;
import com.jjx.inventory.service.impl.*;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.service.ReviewFlowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.jjx.inventory.service.impl.ProductionPickAllocationService.*;

/** 行为替身测试；不宣称真实库事务或浏览器验收。 */
class ProductionPickAllocationTest {
    InventoryOutboundOrderMapper orders = mock(InventoryOutboundOrderMapper.class);
    InventoryOutboundItemMapper items = mock(InventoryOutboundItemMapper.class);
    InventoryStockItemMapper stocks = mock(InventoryStockItemMapper.class);
    MaterialStockAllocationService reservations = mock(MaterialStockAllocationService.class);
    InventoryRollService rolls = mock(InventoryRollService.class);
    ReviewFlowService reviews = mock(ReviewFlowService.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    List<InventoryOutboundItem> rows = new ArrayList<>();
    List<InventoryStockItem> inventory = new ArrayList<>();
    List<ReviewFlow> flows = new ArrayList<>();
    Map<Long, Long> suppliers = new HashMap<>();
    InventoryOutboundOrder order = new InventoryOutboundOrder();
    long nextId = 100;
    ProductionPickAllocationService service;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), InventoryOutboundItem.class);
        order.setOutboundId(1L); order.setWarehouseId(2L); order.setOutboundType("production"); order.setSourceType("work_order");
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        service = new ProductionPickAllocationService(orders, items, stocks, reservations, rolls, jdbc, reviews) {
            @Override protected Supplier supplier(Long id) { return new Supplier(suppliers.get(id), "supplier-" + suppliers.get(id)); }
        };
        when(orders.selectById(1L)).thenReturn(order);
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        when(orders.updateById(any(InventoryOutboundOrder.class))).thenReturn(1);
        when(orders.update(any(InventoryOutboundOrder.class),any())).thenReturn(1);
        when(items.selectByOutboundId(1L)).thenAnswer(inv -> new ArrayList<>(rows));
        when(items.update(any(InventoryOutboundItem.class), any())).thenAnswer(inv -> {
            InventoryOutboundItem row = inv.getArgument(0); rows.removeIf(r -> r.getItemId().equals(row.getItemId())); rows.add(row); return 1;
        });
        when(items.insert(any(InventoryOutboundItem.class))).thenAnswer(inv -> { InventoryOutboundItem row=inv.getArgument(0); row.setItemId(nextId++); rows.add(row); return 1; });
        when(items.deleteById(anyLong())).thenAnswer(inv -> rows.removeIf(r -> r.getItemId().equals(inv.getArgument(0))) ? 1 : 0);
        when(stocks.selectProductionPickCandidates(anyLong(), eq(2L), anyInt())).thenAnswer(inv -> availableBatches(inv.getArgument(0)));
        when(stocks.selectProductionPickCandidatesReadOnly(anyLong(), eq(2L), anyInt())).thenAnswer(inv -> availableBatches(inv.getArgument(0)));
        when(stocks.selectByIdForUpdate(anyLong())).thenAnswer(inv -> inventory.stream().filter(s -> s.getItemId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(stocks.selectById(anyLong())).thenAnswer(inv -> inventory.stream().filter(s -> s.getItemId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(reviews.record(anyString(), anyLong(), anyString(), anyString(), any(), any(), any(), isNull())).thenAnswer(inv -> {
            ReviewFlow flow = new ReviewFlow(); flow.setActionCode(inv.getArgument(2)); flows.add(flow); return flow;
        });
        when(reviews.updateById(any(ReviewFlow.class))).thenReturn(true);
        when(reviews.listByBiz(anyString(), anyLong())).thenReturn(flows);
    }
    List<InventoryStockItem> availableBatches(Long material) { return inventory.stream().filter(s -> material.equals(s.getMaterialId()) && Objects.equals(s.getWarehouseId(), 2L) && s.getQuantity().signum()>0).toList(); }
    InventoryOutboundItem demand(long id, long bom, String quantity) {
        InventoryOutboundItem row=new InventoryOutboundItem(); row.setItemId(id); row.setOutboundId(1L); row.setMaterialId(3L);
        row.setMaterialCode("M3"); row.setMaterialName("material"); row.setUnit("PCS"); row.setSourceBomItemId(bom);
        row.setCoverageQuantity(new BigDecimal(quantity)); row.setQuantity(new BigDecimal(quantity)); rows.add(row); return row;
    }
    InventoryStockItem batch(long id, String quantity, long supplier) {
        InventoryStockItem s=new InventoryStockItem(); s.setItemId(id); s.setMaterialId(3L); s.setInventoryItemId(30L); s.setMaterialCode("M3");
        s.setWarehouseId(2L); s.setLocationId(20L); s.setBatchNo("B"+id); s.setQuantity(new BigDecimal(quantity));
        s.setReservedQuantity(BigDecimal.ZERO); s.setStatus(StockItemStatusEnum.ACTIVE.getValue()); s.setUnitCost(BigDecimal.ONE);
        inventory.add(s); suppliers.put(id,supplier); return s;
    }
    BigDecimal sum() {return rows.stream().map(InventoryOutboundItem::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);}

    @Test void zeroStockKeepsOneShortageAndRepeatedCallsDoNotGrowDemand() {
        demand(1,11,"10"); service.allocate(1L); service.allocate(1L);
        assertEquals(1,rows.size()); assertEquals(0,new BigDecimal("10").compareTo(sum())); assertNull(rows.getFirst().getStockItemId());
        verifyNoInteractions(reservations,rolls);
    }
    @Test void twoBomDemandsShareOneBatchWithoutDoubleAllocationAndKeepCoverageOnce() {
        demand(1,11,"6"); demand(2,12,"6"); batch(21,"10",7);
        service.allocate(1L); service.allocate(1L);
        assertEquals(3,rows.size()); assertEquals(0,new BigDecimal("12").compareTo(sum()));
        assertEquals(0,new BigDecimal("10").compareTo(rows.stream().filter(r->r.getStockItemId()!=null).map(InventoryOutboundItem::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertEquals(0,new BigDecimal("12").compareTo(rows.stream().map(r->r.getCoverageQuantity()==null?BigDecimal.ZERO:r.getCoverageQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertEquals(1,rows.stream().filter(r->r.getStockItemId()==null).count());
        verify(reservations,never()).consume(any(),any());
    }
    @Test void reallocationActuallyReplacesExhaustedBatchWithoutChangingDemand() {
        demand(1,11,"8"); InventoryStockItem a=batch(21,"8",7); service.allocate(1L);
        a.setQuantity(BigDecimal.ZERO); batch(22,"8",7); service.allocate(1L);
        assertEquals(1,rows.size()); assertEquals(22L,rows.getFirst().getStockItemId()); assertEquals(0,new BigDecimal("8").compareTo(sum()));
    }
    @Test void saveRejectsCrossDemandOverAllocationAndStaleFingerprint() {
        demand(1,11,"6"); demand(2,12,"6"); batch(21,"10",7);
        String fingerprint=service.view(1L).fingerprint();
        assertThrows(BusinessException.class,()->service.save(1L,new SaveRequest(fingerprint,List.of(new AllocationLine(1L,21L,new BigDecimal("6")),new AllocationLine(2L,21L,new BigDecimal("6"))))));
        verify(items,never()).update(any(InventoryOutboundItem.class),any());
        assertThrows(BusinessException.class,()->service.save(1L,new SaveRequest("stale",List.of())));
    }
    @Test void ordinaryBatchIssuesWithoutRollServiceAndInsufficientBatchDemandsReallocation() {
        demand(1,11,"6"); InventoryStockItem stock=batch(21,"8",7); service.allocate(1L);
        assertSame(stock,service.issueStock(order,rows.getFirst())); verifyNoInteractions(rolls);
        stock.setQuantity(new BigDecimal("5"));
        BusinessException e=assertThrows(BusinessException.class,()->service.validateForIssue(order,rows));
        assertTrue(e.getMessage().contains("B21")); assertTrue(e.getMessage().contains("缺口 1")); assertTrue(e.getMessage().contains("重排"));
    }
    @Test void completeRollOnlyAndReadOnlyOwnedReservationsDoNotReleaseAnything() {
        demand(1,11,"6"); InventoryStockItem stock=batch(21,"10",7); stock.setRollNo("ROLL21");
        service.allocate(1L); assertNull(rows.getFirst().getStockItemId());
        assertThrows(BusinessException.class,()->service.save(1L,new SaveRequest(service.view(1L).fingerprint(),List.of(new AllocationLine(1L,21L,new BigDecimal("6"))))));
        rows.getFirst().setQuantity(new BigDecimal("10")); stock.setReservedQuantity(new BigDecimal("4"));
        when(reservations.ownedQuantity(order,21L)).thenReturn(new BigDecimal("4"));
        service.allocate(1L); assertEquals("ROLL21",rows.getFirst().getRollNo());
        assertEquals(new BigDecimal("4"),stock.getReservedQuantity()); verify(reservations,never()).consume(any(),any());
    }
    @Test void otherOrderReservationIsExcludedAndRemainsUnchanged() {
        demand(1,11,"10"); var stock=batch(21,"10",7); stock.setReservedQuantity(new BigDecimal("4"));
        service.allocate(1L); assertEquals(2,rows.size());
        assertEquals(0,new BigDecimal("6").compareTo(rows.stream().filter(r->r.getStockItemId()!=null).findFirst().orElseThrow().getQuantity()));
        assertEquals(new BigDecimal("4"),stock.getReservedQuantity());
    }
    @Test void mixedSupplierNeedsReviewAndChangingAllocationInvalidatesApproval() {
        demand(1,11,"10"); batch(21,"5",7); batch(22,"5",8); service.allocate(1L);
        assertEquals(InventoryOrderStatusEnum.MIXED_BATCH_PENDING.getValue(),order.getOrderStatus());
        assertThrows(BusinessException.class,()->service.validateApproval(order));
        AllocationView view=service.view(1L); service.review(1L,new ReviewRequest(view.fingerprint(),true,"approved"));
        service.validateForIssue(order,rows);
        assertEquals("APPROVE",flows.getLast().getActionCode());
        batch(23,"10",9); inventory.getFirst().setQuantity(BigDecimal.ZERO); service.allocate(1L);
        assertThrows(BusinessException.class,()->service.validateForIssue(order,rows));
        assertEquals(InventoryOrderStatusEnum.MIXED_BATCH_PENDING.getValue(),order.getOrderStatus());
    }
    @Test void unknownSupplierBlocksApprovalAndIssueAndRejectNeedsReason() {
        demand(1,11,"5"); batch(21,"5",7); suppliers.clear(); service.allocate(1L);
        var view=service.view(1L); assertTrue(view.unknownSupplier());
        assertTrue(assertThrows(BusinessException.class,()->service.validateForIssue(order,rows)).getMessage().contains("供应商未知"));
        assertThrows(BusinessException.class,()->service.review(1L,new ReviewRequest(view.fingerprint(),true,"")));
        assertThrows(BusinessException.class,()->service.review(1L,new ReviewRequest(view.fingerprint(),false,"")));
        service.review(1L,new ReviewRequest(view.fingerprint(),false,"unknown supplier"));
        assertEquals(InventoryOrderStatusEnum.REJECTED.getValue(),order.getOrderStatus());
        assertNull(order.getRemark());
    }
    @Test void nonProductionAndIssuedDocumentsCannotBeReallocated() {
        order.setOutboundType("sales"); assertThrows(BusinessException.class,()->service.allocate(1L));
        order.setOutboundType("production"); order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());
        assertThrows(BusinessException.class,()->service.allocate(1L)); verifyNoInteractions(items);
    }
}
