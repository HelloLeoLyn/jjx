-- risk: high
-- task: dev-20261008-017
-- 仅删除两条手动删除产品后遗留的库存身份；保留产品及物料等其他数据。
-- 通过 scripts/db-migrate.sh 校验现有手工备份后执行。重复执行不影响其他记录。
START TRANSACTION;

DELETE FROM inventory_item
WHERE item_type = 'PRODUCT'
  AND ((inventory_item_id = 2084 AND source_id = 25 AND item_code = 'JST002MEOO')
    OR (inventory_item_id = 2085 AND source_id = 26 AND item_code = 'JST003MEOO'))
  AND NOT EXISTS (SELECT 1 FROM product p WHERE p.product_id = inventory_item.source_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_stock s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_stock_item s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_transaction s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_inbound_item s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_outbound_item s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_stocktake_item s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_transfer_item s WHERE s.inventory_item_id = inventory_item.inventory_item_id)
  AND NOT EXISTS (SELECT 1 FROM sales_order_stock_reserve s WHERE s.inventory_item_id = inventory_item.inventory_item_id);

SELECT ROW_COUNT() AS deleted_orphan_product_inventory_items;
COMMIT;
