-- dev-20260930-010
-- 清理「孤儿 PRODUCT 库存身份行」：inventory_item 中 item_type='PRODUCT' 但来源产品已不存在，
-- 且没有任何库存/单据引用的残留行。
--
-- 背景：产品被物理删除（早期清测试数据清理 / 草稿回收释放编码）时未同步删除其统一库存身份行，
--       导致 item_code 与后续复用的产品编码撞 uk_inventory_item_code 唯一键；
--       InventoryItemServiceImpl.ensure() 撞键后按 source_id 回查为空 → 返回 null → 调用方 NPE
--       → 异常被审核联动的 try/catch 吞掉 → 同一物理事务被标 rollback-only → 订单审核整体回滚。
--
-- 幂等：只删「无产品 + 无任何引用」的行；重复执行影响 0 行。
-- 安全：已核验这些行在 inventory_stock / inventory_stock_item / sales_order_stock_reserve /
--       inventory_inbound_item / inventory_outbound_item / inventory_stocktake_item /
--       inventory_transfer_item / inventory_transaction 中均无引用。

DELETE ii
FROM inventory_item ii
WHERE ii.item_type = 'PRODUCT'
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.product_id = ii.source_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_stock s WHERE s.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_stock_item si WHERE si.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_inbound_item ib WHERE ib.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_outbound_item ob WHERE ob.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_stocktake_item st WHERE st.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_transfer_item tf WHERE tf.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_transaction tr WHERE tr.inventory_item_id = ii.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM sales_order_stock_reserve r WHERE r.inventory_item_id = ii.inventory_item_id);

-- 核验：应为 0 行（无孤儿 PRODUCT 库存身份）
-- SELECT COUNT(*) AS orphan_product_items FROM inventory_item ii
-- WHERE ii.item_type='PRODUCT' AND NOT EXISTS (SELECT 1 FROM product p WHERE p.product_id=ii.source_id);
