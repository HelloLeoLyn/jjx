package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.MaterialReserveStatusEnum;
import com.jjx.inventory.mapper.OrderMaterialReserveMapper;
import com.jjx.inventory.measurement.*;
import com.jjx.inventory.service.impl.*;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.sales.mapper.OrderMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H2真实流水/批次/预留与Spring事务；业务单故障必须连预留消费一起回滚。 */
class DualUnitBusinessLifecycleTest extends RollFixture {
    OrderMaterialReserveMapper reservations;
    MaterialStockAllocationService allocations;

    @BeforeEach void configureAllocations() throws Exception {
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(OrderMaterialReserveMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setConfiguration(config);factory.setDataSource(manager.getDataSource());
        reservations=new SqlSessionTemplate(factory.getObject()).getMapper(OrderMaterialReserveMapper.class);
        createTable(OrderMaterialReserve.class);
        allocations=proxy(new MaterialStockAllocationService(stocks,summaries,reservations,mock(ProductionOrderMapper.class),mock(OrderMapper.class)),MaterialStockAllocationService.class);
        ReflectionTestUtils.setField((Object)AopTestUtils.getTargetObject(rolls),"allocations",allocations);
        ReflectionTestUtils.setField((Object)AopTestUtils.getTargetObject(outbound),"materialAllocations",allocations);
    }
    OrderMaterialReserve reserve(long order,String demand) {
        var row=new OrderMaterialReserve();row.setOrderId(order);row.setOrderNo("SO-"+order);row.setMaterialId(11L);
        row.setMaterialCode("AUX-TEST");row.setStatus(MaterialReserveStatusEnum.ACTIVE.getValue());
        allocations.allocate(row,bd(demand));return reservations.selectActiveByOrder(order).get(0);
    }
    long ownIssue(Long stockId,long owner) {
        var stock=stocks.selectById(stockId);var item=new HashMap<String,Object>();
        item.put("stockItemId",stockId);item.put("rollNo",stock.getRollNo());item.put("materialId",11L);item.put("inventoryItemId",31L);
        item.put("materialCode","AUX-TEST");item.put("materialName","卷材");item.put("unit","㎡");item.put("quantity",stock.getQuantity());
        return outbound.create(Map.of("outboundType","other","sourceType","sales_order","sourceId",owner,"warehouseId",1L,"items",List.of(item)));
    }
    @Test void ownerConsumesWholeRollAndAnotherOrderCannotTakeItAndDoubleConfirmIsIdempotent() {
        long stock=receive(1).get(0);var row=reserve(101,"4");
        eq("4",row.getDemandQuantity());eq("9.75",row.getReserveQuantity());
        assertThrows(BusinessException.class,()->ownIssue(stock,202));
        long document=ownIssue(stock,101);assertTrue(outbound.confirm(document,1L,"test"));
        assertFalse(outbound.confirm(document,1L,"test"));
        eq("0",stocks.selectById(stock).getReservedQuantity());eq("0",balance());
        var consumed=reservations.selectById(row.getId());eq("9.75",consumed.getConsumedQuantity());
        assertEquals(MaterialReserveStatusEnum.CONSUMED.getValue(),consumed.getStatus());consistent();
    }
    @Test void issueFailureRollsBackOwnConsumptionAndStockAndAppendOnlyLedger() {
        long stock=receive(1).get(0);var row=reserve(101,"4");long document=ownIssue(stock,101);
        jdbc.execute("ALTER TABLE inventory_transaction ADD CONSTRAINT deny_issue CHECK (quantity>=0)");
        assertThrows(Exception.class,()->outbound.confirm(document,1L,"test"));
        eq("0",reservations.selectById(row.getId()).getConsumedQuantity());
        eq("9.75",stocks.selectById(stock).getReservedQuantity());eq("9.75",balance());assertEquals(1,txs.selectCount(null));consistent();
    }
    @Test void releasePreservesStockQuantityAndMakesTheWholeRollAvailable() {
        long stock=receive(1).get(0);var row=reserve(101,"4");
        new TransactionTemplate(manager).executeWithoutResult(t->{allocations.release(row);row.setStatus(MaterialReserveStatusEnum.RELEASED.getValue());reservations.updateById(row);});
        eq("0",stocks.selectById(stock).getReservedQuantity());eq("9.75",balance());
        assertDoesNotThrow(()->ownIssue(stock,202));consistent();
    }
    @Test void reservingAgainDoesNotReoccupyDemandAlreadyCoveredByAnIssuedWholeRoll() {
        long stock=receive(1).get(0);reserve(101,"4");long document=ownIssue(stock,101);
        assertTrue(outbound.confirm(document,1L,"test"));
        var orders=mock(OrderMapper.class);var order=new com.jjx.sales.domain.entity.SalesOrder();order.setOrderId(101L);order.setOrderNo("SO-101");
        when(orders.selectByIdForUpdate(101L)).thenReturn(order);
        var products=mock(com.jjx.sales.mapper.SalesOrderProductMapper.class);
        var product=new com.jjx.sales.domain.entity.SalesOrderProduct();product.setProductId(77L);product.setQuantity(1);
        when(products.selectList(any())).thenReturn(List.of(product));
        var boms=mock(com.jjx.product.mapper.EngineeringBomMapper.class);
        var bom=new com.jjx.engineering.domain.entity.EngineeringBom();bom.setBomId(88L);when(boms.selectOne(any())).thenReturn(bom);
        var items=mock(com.jjx.product.mapper.EngineeringBomItemMapper.class);
        var item=new com.jjx.engineering.domain.entity.EngineeringBomItem();item.setMaterialId(11L);item.setMaterialCode("AUX-TEST");item.setActualIssueQty(bd("4"));
        when(items.selectList(any())).thenReturn(List.of(item));
        var service=proxy(new OrderMaterialReserveServiceImpl(reservations,allocations,orders,products,boms,items),com.jjx.inventory.service.OrderMaterialReserveService.class);
        assertDoesNotThrow(()->service.reserveForOrder(101L,3));
        assertTrue(reservations.selectActiveByOrder(101L).isEmpty());eq("0",balance());consistent();
    }
    @Test void frozenActualRollLengthIsUsedForReturnAndNewArchiveDefaultsDoNotRewriteHistory() {
        long stock=receive(1).get(0);
        var policy=new MeasurementPolicy("ROLL","㎡",MeasurementPolicy.Mode.ROLL_AREA,null);
        String snapshot=InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(BigDecimal.ONE,bd("195"),null,bd("50"),null,null,null)));
        jdbc.update("UPDATE inventory_stock_item SET measurement_snapshot=? WHERE item_id=?",snapshot,stock);
        long document=issue(List.of(stock));var issued=outItems.selectByOutboundId(document).get(0);
        var material=materials.selectById(11L);material.setLengthMm(bd("999000"));
        long returned=rolls.returnRoll(returnInput(issued,"40",1L,null));
        var actual=inItems.selectByInboundId(returned).get(0);eq("7.8",actual.getQuantity());
        eq("40",InventoryMeasurementSnapshotService.read(actual.getMeasurementSnapshot()).result().actual().rollLengthM());
        eq("50",InventoryMeasurementSnapshotService.read(issued.getMeasurementSnapshot()).result().actual().rollLengthM());
        assertEquals(issued.getRollNo(),actual.getRollNo());consistent();
    }
    @Test void kilogramReturnRequiresActualWeightAndDoesNotUseNominalRollLength() {
        long stock=receive(1).get(0);var material=materials.selectById(11L);material.setUnit("KG");
        var ii=inventoryItems.selectById(31L);ii.setUnit("KG");
        var policy=new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT,null);
        String snapshot=InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(BigDecimal.ONE,bd("195"),null,bd("50"),null,null,bd("9.75"))));
        jdbc.update("UPDATE inventory_stock_item SET measurement_snapshot=? WHERE item_id=?",snapshot,stock);
        var header=new InventoryOutboundOrder();header.setOutboundNo("KG-OUT");header.setSourceType("other");header.setWarehouseId(1L);header.setOrderStatus(com.jjx.inventory.enums.InventoryOrderStatusEnum.COMPLETED.getValue());outs.insert(header);
        var line=new InventoryOutboundItem();line.setOutboundId(header.getOutboundId());line.setStockItemId(stock);line.setUnit("KG");line.setQuantity(bd("9.75"));outItems.insert(line);
        new TransactionTemplate(manager).executeWithoutResult(t->{var actual=rolls.issueStock(header,line);outItems.updateById(line);
            var flow=new InventoryTransaction();flow.setTransactionType("OUTBOUND");flow.setSourceId(header.getOutboundId());flow.setSourceItemId(line.getItemId());mutation.applyDelta(actual,bd("-9.75"),flow);});
        assertThrows(BusinessException.class,()->rolls.returnRoll(returnInput(line,"40",1L,null)));
        var actual=returnInput(line,"40",1L,null);actual.setActualWeightKg(bd("6"));
        long returned=rolls.returnRoll(actual);var item=inItems.selectByInboundId(returned).get(0);
        eq("6",item.getQuantity());eq("7.8",MeasurementCalculator.inventoryArea(InventoryMeasurementSnapshotService.read(item.getMeasurementSnapshot()).result()));consistent();
        // 再次领出的是40m余卷，不能以更早50m原卷长度为返还上限。
        var again=new InventoryOutboundOrder();again.setOutboundNo("KG-OUT-2");again.setSourceType("other");again.setWarehouseId(1L);
        again.setOrderStatus(com.jjx.inventory.enums.InventoryOrderStatusEnum.COMPLETED.getValue());outs.insert(again);
        var next=new InventoryOutboundItem();next.setOutboundId(again.getOutboundId());next.setStockItemId(stock);next.setUnit("KG");next.setQuantity(bd("6"));outItems.insert(next);
        new TransactionTemplate(manager).executeWithoutResult(t->{var actualStock=rolls.issueStock(again,next);outItems.updateById(next);
            var flow=new InventoryTransaction();flow.setTransactionType("OUTBOUND");flow.setSourceId(again.getOutboundId());flow.setSourceItemId(next.getItemId());mutation.applyDelta(actualStock,bd("-6"),flow);});
        var excess=returnInput(next,"45",1L,null);excess.setActualWeightKg(bd("5"));
        assertThrows(BusinessException.class,()->rolls.returnRoll(excess));consistent();
    }
}
