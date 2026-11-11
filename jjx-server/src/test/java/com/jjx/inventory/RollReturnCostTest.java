package com.jjx.inventory;

import com.jjx.inventory.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RollReturnCostTest extends RollFixture {
    @Test void returnUsesPostedIssueCostWhenCurrentPriceChanges() {
        long stock=receive(1).get(0), issueId=issue(List.of(stock));
        var issued=outItems.selectByOutboundId(issueId).get(0);
        eq("2",issued.getUnitPrice());
        // 模拟当前档案/零余额库存估价改变；原实发流水不改。
        jdbc.update("UPDATE inventory_stock_item SET unit_cost=99 WHERE item_id=?",stock);
        long returned=rolls.returnRoll(returnInput(issued,"25",1L,null));
        var item=inItems.selectByInboundId(returned).get(0); eq("2",item.getUnitPrice());
        eq("9.75",item.getAmount()); eq("2",stocks.selectById(stock).getUnitCost());
        var flow=txs.selectRollForUpdate("ROLL-1"); eq("2",flow.get(flow.size()-1).getUnitCost()); consistent();
    }
}
