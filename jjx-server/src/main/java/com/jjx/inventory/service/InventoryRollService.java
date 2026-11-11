package com.jjx.inventory.service;

import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.save.RollReturnSaveDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 卷材以㎡记账。原始尺寸是事实，余长由面积派生。 */
public interface InventoryRollService {
    BigDecimal area(BigDecimal widthMm, BigDecimal lengthM);
    void normalizeInbound(InventoryInboundItem item, Map<?, ?> input);
    InventoryStockItem initialStock(InventoryInboundOrder order, InventoryInboundItem item, BigDecimal delta);
    void normalizeOutbound(InventoryOutboundOrder order, InventoryOutboundItem item, Map<?, ?> input);
    InventoryStockItem issueStock(InventoryOutboundOrder order, InventoryOutboundItem item);
    Long returnRoll(RollReturnSaveDTO input);
    Long reverseReturn(Long itemId, String reason);
    Long reverseIssue(Long itemId, String reason);
    List<InventoryTransaction> flow(String rollNo);
}
