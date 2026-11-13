package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RollInventoryCompatibilityTest extends RollFixture {
    @Test void nonRollInboundAndLegacyFlowKeepOriginalAreaBehaviour() {
        var order=new InventoryInboundOrder(); order.setInboundNo("LEGACY"); order.setInboundType("other");
        order.setSourceType("other"); order.setWarehouseId(1L); order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue()); ins.insert(order);
        var item=new InventoryInboundItem(); item.setInboundId(order.getInboundId()); item.setInventoryItemId(31L);
        item.setMaterialId(11L); item.setMaterialCode("AUX-TEST"); item.setMaterialName("legacy"); item.setUnit("㎡");
        item.setBatchNo("LEGACY-BATCH"); item.setQuantity(bd("3")); item.setUnitPrice(bd("2")); inItems.insert(item);
        assertTrue(inbound.confirm(order.getInboundId(),1L,"test"));
        var stock=stocks.selectList(null).get(0); assertNull(stock.getRollNo()); eq("3",stock.getQuantity());
        var tx=txs.selectList(null).get(0); assertNull(tx.getRollNo());
        // dev-20261111-012(dd799353) 起流水统一回填 stock_item_id；非卷材入库同样要指向库存批次。
        assertNotNull(tx.getStockItemId()); assertEquals(stock.getItemId(), tx.getStockItemId());
        var adjustment=new InventoryTransaction(); adjustment.setTransactionType(TransactionTypeEnum.ADJUST.getCode());
        mutation.applyDelta(stock,bd("-1"),adjustment); eq("2",balance());
    }
    @Test void rolledStockCannotBeSilentlySplitByLegacyFifoOrOtherMutationPaths() {
        long stock=receive(1).get(0);
        var order=new InventoryOutboundOrder(); order.setOutboundNo("NO-ROLL-SELECT"); order.setOutboundType("other");
        order.setSourceType("other"); order.setWarehouseId(1L); order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue()); outs.insert(order);
        var item=new InventoryOutboundItem(); item.setOutboundId(order.getOutboundId()); item.setInventoryItemId(31L);
        item.setMaterialId(11L); item.setMaterialCode("AUX-TEST"); item.setMaterialName("卷材"); item.setQuantity(bd("1")); outItems.insert(item);
        assertThrows(BusinessException.class,() -> outbound.confirm(order.getOutboundId(),1L,"test"));
        var tx=new InventoryTransaction(); tx.setTransactionType(TransactionTypeEnum.TRANSFER_OUT.getCode());
        assertThrows(BusinessException.class,() -> mutation.applyDelta(stocks.selectById(stock),bd("-1"),tx));
        eq("9.75",balance()); assertEquals(1,txs.selectCount(null));
    }
    @Test void genericCreateCannotBypassControlledProductionPickForRolls() {
        long id=receive(1).get(0);
        assertThrows(BusinessException.class,() -> outbound.create(Map.of("outboundType","production","sourceType","work_order",
                "sourceId",777L,"warehouseId",1L,"items",List.of(Map.of("stockItemId",id,"inventoryItemId",31L,
                        "materialId",11L,"materialCode","AUX-TEST","materialName","卷材","unit","㎡","quantity",bd("9.75"))))));
        assertEquals(0,outs.selectCount(null)); eq("9.75",balance()); assertEquals(1,txs.selectCount(null));
    }
    @Test void initialRollValidatesMasterIdentityUnitsAndWholeRelease() {
        var item=new InventoryInboundItem(); item.setInventoryItemId(31L); item.setMaterialId(11L); item.setMaterialCode("AUX-TEST");
        item.setUnit("㎡"); item.setBatchNo("B1");
        rolls.normalizeInbound(item,Map.of("rollNo","R","rollWidthMm",195,"rollLengthM",50)); eq("9.75",item.getQuantity());
        item.setItemId(999L);
        var order=new InventoryInboundOrder(); order.setWarehouseId(1L);
        assertThrows(BusinessException.class,() -> rolls.initialStock(order,item,bd("4.875")));
        assertThrows(BusinessException.class,() -> rolls.normalizeInbound(item,Map.of("rollNo","R","rollWidthMm",200,"rollLengthM",50)));
        item.setUnit("卷"); assertThrows(BusinessException.class,() -> rolls.normalizeInbound(item,Map.of("rollNo","R","rollWidthMm",195,"rollLengthM",50)));
        item.setUnit("㎡"); item.setMaterialCode("WRONG");
        assertThrows(BusinessException.class,() -> rolls.normalizeInbound(item,Map.of("rollNo","R","rollWidthMm",195,"rollLengthM",50)));
        assertThrows(BusinessException.class,() -> rolls.area(bd("0"),bd("50")));
    }
    @Test void gateAggregatesSameBatchAndChecksEachRollWithoutBorrowingLegacyFlow() throws Exception {
        receive(2);
        assertEquals(0,gateCount("batch_drift_sql"));
        assertEquals(0,gateCount("roll_drift_sql"));
        assertEquals(0,gateCount("roll_orphan_sql"));
        // H2故障夹具：批次合计看似平账，但新卷掺入历史无行ID流水，应被逐行检查抓住。
        jdbc.update("UPDATE inventory_stock_item SET quantity=19.5 WHERE item_id=1");
        jdbc.update("INSERT INTO inventory_transaction(inventory_item_id,batch_no,quantity) VALUES(31,'IQC-SHARED',9.75)");
        assertEquals(0,gateCount("batch_drift_sql"));
        assertEquals(1,gateCount("roll_drift_sql"));
        jdbc.update("UPDATE inventory_transaction SET warehouse_id=9 WHERE stock_item_id=2");
        assertEquals(1,gateCount("roll_orphan_sql"));
    }
    private int gateCount(String variable) throws Exception {
        String source=java.nio.file.Files.readString(java.nio.file.Path.of("../scripts/check-stock-summary.sh"));
        var matcher=java.util.regex.Pattern.compile("(?s)"+variable+"=\"(.*?)\"").matcher(source);
        assertTrue(matcher.find(),variable);
        String sql=matcher.group(1).replace("<=>","IS NOT DISTINCT FROM");
        return jdbc.queryForObject("SELECT COUNT(*) FROM ("+sql+") d",Integer.class);
    }

}
