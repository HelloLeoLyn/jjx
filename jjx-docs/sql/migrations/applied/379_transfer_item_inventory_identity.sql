-- dev-20261119-004：成品与材料均以 inventory_item_id 关联移库明细。
-- 成品没有 material_id，允许为空；不回填/改写任何现有明细。
SET @location_transfer_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='inventory_transfer_item' AND COLUMN_NAME='material_id' AND IS_NULLABLE='NO'),
  'ALTER TABLE inventory_transfer_item MODIFY COLUMN material_id BIGINT NULL COMMENT ''物料ID，成品为空，统一身份见inventory_item_id''',
  'SELECT 1');
PREPARE location_transfer_stmt FROM @location_transfer_sql;
EXECUTE location_transfer_stmt;
DEALLOCATE PREPARE location_transfer_stmt;
