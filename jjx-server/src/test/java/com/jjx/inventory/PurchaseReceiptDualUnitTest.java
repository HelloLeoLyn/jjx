package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.dto.save.RollReceiptPlanSaveDTO;
import com.jjx.inventory.service.impl.InventoryRollServiceImpl;
import com.jjx.purchase.domain.entity.*;
import com.jjx.purchase.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

/** 真实H2 Mapper/事务：实测过账、成本折算、容差及失败回滚；不代表运行态MySQL。 */
class PurchaseReceiptDualUnitTest extends RollFixture {
    @Test void exactFiveRollsCostPerSquareMetreRatherThanPerRoll() {
        var item = purchaseReceipt("48.75","48.75");
        item.setAmount(bd("2500")); item.setUnitPrice(bd("51.2821")); inItems.updateById(item);
        inbound.saveRollReceiptPlan(item.getItemId(), plan("R1","R2","R3","R4","R5"));
        assertTrue(inbound.confirm(item.getInboundId(),1L,"test"));
        for (var stock : stocks.selectList(null)) eq("51.2821",stock.getUnitCost());
        for (var tx : txs.selectList(null)) { eq("51.2821",tx.getUnitCost()); eq("500",tx.getAmount()); assertTrue(tx.getRemark().contains("实测面积=48.75㎡/允许面积=48.75㎡/差异率=0.0000%")); }
        assertTrue(ins.selectById(item.getInboundId()).getRemark().contains("实测面积=48.75"));
        consistent();
    }
    @Test void withinTolerancePostsMeasuredAreaAndKeepsOriginalAllowedAndPurchaseFacts() {
        var item = purchaseReceipt("48.75","48.75"); item.setAmount(bd("2500")); item.setUnitPrice(bd("51.2821")); inItems.updateById(item);
        var p = measuredPlan("51");
        inbound.saveRollReceiptPlan(item.getItemId(),p);
        assertTrue(inbound.confirm(item.getInboundId(),1L,"test")); eq("49.725",balance());
        eq("48.75",inItems.selectById(item.getItemId()).getQuantity());
        eq("48.75",inItems.selectById(item.getItemId()).getAcceptedQuantity());
        eq("48.75",inItems.selectById(item.getItemId()).getPostedQuantity());
        eq("2500",inItems.selectById(item.getItemId()).getAmount());
        for(var tx:txs.selectList(null)) { eq("50.2765",tx.getUnitCost()); assertTrue(tx.getRemark().contains("差异率=2.0000%")); }
        assertFalse(inbound.confirm(item.getInboundId(),1L,"test")); consistent();
    }
    @Test void inclusiveBoundariesAndOutsideToleranceCannotSaveOrPost() {
        assertTrue(InventoryRollServiceImpl.areaToleranceRemark(bd("95"),bd("100")).contains("-5.0000%"));
        assertTrue(InventoryRollServiceImpl.areaToleranceRemark(bd("105"),bd("100")).contains("5.0000%"));
        for (String n : List.of("94.9999","105.0001"))
            assertTrue(assertThrows(BusinessException.class,()->InventoryRollServiceImpl.areaToleranceRemark(bd(n),bd("100"))).getMessage().contains("改单或走审批"));
        var item=purchaseReceipt("48.75","48.75");
        assertThrows(BusinessException.class,()->inbound.saveRollReceiptPlan(item.getItemId(),measuredPlan("53")));
        assertNull(inItems.selectById(item.getItemId()).getRollPlanJson());
        inbound.saveRollReceiptPlan(item.getItemId(),measuredPlan("50"));
        jdbc.update("UPDATE inventory_inbound_item SET roll_plan_json=? WHERE item_id=?", "[{\"rollNo\":\"TAMPERED\",\"widthMm\":195,\"lengthM\":300}]",item.getItemId());
        assertThrows(BusinessException.class,()->inbound.confirm(item.getInboundId(),1L,"test"));
        assertEquals(0,stocks.selectCount(null)); assertEquals(0,txs.selectCount(null)); eq("0",inItems.selectById(item.getItemId()).getPostedQuantity());
    }
    @Test void receiptGeneratorsBothConvertCostAndMetreMaterialPostsTenPerMetre() {
        var po = new PurchaseOrder(); po.setOrderId(777L); po.setOrderNo("PO-777");
        var orders=mock(PurchaseOrderMapper.class); when(orders.selectById(777L)).thenReturn(po);
        var lines=mock(PurchaseOrderItemMapper.class);
        ReflectionTestUtils.setField(inboundTarget,"purchaseOrderMapper",orders);
        ReflectionTestUtils.setField(inboundTarget,"purchaseOrderItemMapper",lines);
        var metreMaterial = materials.selectById(11L); metreMaterial.setUnit("m");
        inventoryItems.selectById(31L).setUnit("m");
        var source=new PurchaseOrderItem(); source.setItemId(99L); source.setOrderId(777L); source.setMaterialId(11L);
        source.setMaterialCode("AUX-TEST"); source.setMaterialName("卷材"); source.setUnit("m"); source.setPriceUnit("卷");
        source.setPurchaseUnit("卷"); source.setRollCount(5); source.setRollLengthM(bd("50")); source.setRollWidthMm(bd("195"));
        source.setQuantity(bd("250")); source.setReceivedQuantity(bd("250")); source.setAmount(bd("2500")); source.setUnitPrice(bd("500"));
        when(lines.selectItemsByOrderId(777L)).thenReturn(List.of(source));
        long id = inbound.createInboundRecordFromPurchase(777L);
        var created = inItems.selectByInboundId(id).get(0); eq("10",created.getUnitPrice()); assertEquals("m",created.getUnit());
        assertEquals(0,txs.selectCount(null));
        // 保持现有非逐卷米制入库模型；不将m批次改为㎡批次。
        created.setInventoryItemId(31L); created.setAcceptedQuantity(bd("250")); inItems.updateById(created);
        var header=ins.selectById(id);
        ReflectionTestUtils.invokeMethod(inboundTarget,"addStock",header,1L,"test","确认入库");
        eq("250",balance()); eq("10",stocks.selectList(null).get(0).getUnitCost()); eq("10",txs.selectList(null).get(0).getUnitCost());
        // 另一采购入库生成入口仍只建单据，成本也须折算。
        source.setReceivedQuantity(bd("0"));
        po.setOrderId(778L); po.setOrderNo("PO-778");
        when(orders.selectById(778L)).thenReturn(po);
        when(lines.selectItemsByOrderId(778L)).thenReturn(List.of(source));
        long second=inbound.createFromPurchase(778L);
        eq("10",inItems.selectByInboundId(second).get(0).getUnitPrice());
    }
    @Test void secondRollFailureRollsBackTraceAndMeasuredStockTogether() {
        var item=purchaseReceipt("48.75","48.75");
        inbound.saveRollReceiptPlan(item.getItemId(),measuredPlan("51"));
        String before=ins.selectById(item.getInboundId()).getRemark();
        jdbc.execute("ALTER TABLE inventory_transaction ADD CONSTRAINT deny_roll CHECK (roll_no <> 'R5')");
        assertThrows(Exception.class,()->inbound.confirm(item.getInboundId(),1L,"test"));
        assertEquals(before,ins.selectById(item.getInboundId()).getRemark());
        assertEquals(0,stocks.selectCount(null)); assertEquals(0,txs.selectCount(null));
    }
    RollReceiptPlanSaveDTO measuredPlan(String length) {
        var p=plan("R1","R2","R3","R4","R5"); p.getRolls().forEach(e->e.setLengthM(bd(length))); return p;
    }
}
