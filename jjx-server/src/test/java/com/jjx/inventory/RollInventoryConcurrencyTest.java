package com.jjx.inventory;

import com.jjx.inventory.domain.*;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RollInventoryConcurrencyTest extends RollFixture {
    @Test void twoConfirmationsCannotIssueOnePhysicalRollTwice() throws Exception {
        long stockId=receive(1).get(0);
        long first=pendingIssue(stockId), second=pendingIssue(stockId);
        var results=race(() -> outbound.confirm(first,1L,"test"), () -> outbound.confirm(second,1L,"test"));
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        eq("0",balance()); assertEquals(2,txs.selectCount(null)); consistent();
    }
    @Test void twoReturnsSerializeOnOriginalIssueAndOneRollRemainsInStock() throws Exception {
        long stockId=receive(1).get(0), issueId=issue(List.of(stockId));
        var issued=outItems.selectByOutboundId(issueId).get(0);
        var results=race(() -> { rolls.returnRoll(returnInput(issued,"25",1L,null)); return true; },
                () -> { rolls.returnRoll(returnInput(issued,"25",2L,20L)); return true; });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        eq("4.875",balance()); assertEquals(3,txs.selectCount(null)); consistent();
    }
    @Test void purchaseRepeatedConfirmCannotDuplicateAnyPlannedRoll() throws Exception {
        var item=purchaseReceipt("19.5","19.5");
        inbound.saveRollReceiptPlan(item.getItemId(),plan("PO-A","PO-B"));
        var results=race(() -> inbound.confirm(item.getInboundId(),1L,"test"), () -> inbound.confirm(item.getInboundId(),1L,"test"));
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        assertEquals(2,stocks.selectCount(null)); assertEquals(2,txs.selectCount(null));
        eq("19.5",inItems.selectById(item.getItemId()).getPostedQuantity()); consistent();
    }
    @Test void purchasePlanSaveAndConfirmSerializeWithoutReplacingPostedRolls() throws Exception {
        var item=purchaseReceipt("19.5","19.5");
        inbound.saveRollReceiptPlan(item.getItemId(),plan("OLD-A","OLD-B"));
        var results=race(() -> inbound.confirm(item.getInboundId(),1L,"test"),
                () -> inbound.saveRollReceiptPlan(item.getItemId(),plan("NEW-A","NEW-B")));
        assertTrue(results.get(0)); assertEquals(2,stocks.selectCount(null)); assertEquals(2,txs.selectCount(null));
        var original=inItems.selectById(item.getItemId());
        var planned=rolls.readReceiptPlan(original).stream().map(com.jjx.inventory.dto.save.RollReceiptPlanSaveDTO.RollEntry::getRollNo).sorted().toList();
        assertEquals(planned,stocks.selectList(null).stream().map(InventoryStockItem::getRollNo).sorted().toList());
        eq("19.5",original.getQuantity()); eq("19.5",original.getPostedQuantity()); consistent();
    }
    private long pendingIssue(long id) {
        var s=stocks.selectById(id);
        return outbound.create(Map.of("outboundType","other","sourceType","other","warehouseId",1L,"items",List.of(Map.of(
                "stockItemId",id,"rollNo",s.getRollNo(),"inventoryItemId",31L,"materialId",11L,"materialCode","AUX-TEST",
                "materialName","卷材","unit","㎡","quantity",s.getQuantity()))));
    }
    private List<Boolean> race(Callable<Boolean> a,Callable<Boolean> b) throws Exception {
        var barrier=new CyclicBarrier(2); var executor=Executors.newFixedThreadPool(2);
        try {
            var futures=executor.invokeAll(List.of(worker(a,barrier),worker(b,barrier)),10,TimeUnit.SECONDS);
            return List.of(futures.get(0).get(),futures.get(1).get());
        } finally { executor.shutdown(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    private Callable<Boolean> worker(Callable<Boolean> action,CyclicBarrier barrier) {
        return () -> {
            try(var auth=mockStatic(SecurityUtils.class)) {
                auth.when(SecurityUtils::getUserId).thenReturn(1L); auth.when(SecurityUtils::getUsername).thenReturn("test");
                auth.when(SecurityUtils::getDisplayName).thenReturn("test"); barrier.await();
                try { return action.call(); } catch(com.jjx.common.exception.BusinessException expected) { return false; }
            }
        };
    }
}
