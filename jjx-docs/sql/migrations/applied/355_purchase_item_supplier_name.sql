-- dev-20261110-008：采购订单明细供应商品名快照；仅回填空值，不覆盖已有快照。
SET @supplier_name_column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order_item' AND COLUMN_NAME = 'supplier_material_name');
SET @supplier_name_ddl = IF(@supplier_name_column_exists = 0,
    'ALTER TABLE purchase_order_item ADD COLUMN supplier_material_name varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''供应商品名快照（dev-20261110-008）''',
    'SELECT 1');
PREPARE supplier_name_stmt FROM @supplier_name_ddl;
EXECUTE supplier_name_stmt;
DEALLOCATE PREPARE supplier_name_stmt;
UPDATE purchase_order_item item
JOIN inventory_material material ON material.material_id = item.material_id
SET item.supplier_material_name = material.supplier_material_name
WHERE (item.supplier_material_name IS NULL OR TRIM(item.supplier_material_name) = '')
  AND material.supplier_material_name IS NOT NULL;
SELECT ROW_COUNT() AS historical_backfilled_rows;
