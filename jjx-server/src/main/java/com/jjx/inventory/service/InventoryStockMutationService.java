package com.jjx.inventory.service;

import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;

import java.math.BigDecimal;

/**
 * 库存数量唯一写入口：dev-20260922-019/028。
 * 每次变动固定在同一事务内完成：批次明细→汇总刷新→流水。
 */
public interface InventoryStockMutationService {

    /**
     * @param stock 已存在或待新建的批次明细；新建时 itemId 为 null
     * @param delta 正数入库，负数出库/冲减
     * @param transaction 流水业务上下文，数量及变动前后由本服务统一填写
     */
    InventoryStockItem applyDelta(InventoryStockItem stock, BigDecimal delta, InventoryTransaction transaction);
}
