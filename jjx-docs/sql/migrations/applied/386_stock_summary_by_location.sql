-- 386_stock_summary_by_location.sql
-- 任务码：dev-20261125-003
-- 目的：库存汇总 inventory_stock 从「一个物料一条」改为「物料 + 库位一条」，支持同一物料多库位。
-- risk：高（改库存汇总唯一键并重建汇总；涉及可用量/预留）
-- 回滚：从当日全库备份恢复；或手工删除 uk_inventory_item_location、恢复 uk_inventory_item 并按物料重建汇总。
-- 幂等：库位 insert 带 NOT EXISTS；索引变更用 information_schema 判断；汇总重建为覆盖式。

SET NAMES utf8mb4;

-- 1) 每个仓库一个「未分配」库位（历史库存/未指定库位的归集位置）
INSERT INTO inventory_storage_location
  (warehouse_id, location_code, location_name, location_type, status, capacity_enabled, sort_order, remark, create_by, create_time)
SELECT w.warehouse_id, CONCAT(w.warehouse_code, '-L999999'), '未分配', 'normal', '0', 0, 999999,
       '系统默认：历史库存/未指定库位的归集位置', 'migration', NOW()
FROM inventory_warehouse w
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_storage_location l
  WHERE l.warehouse_id = w.warehouse_id AND l.location_name = '未分配'
);

-- 2) 库存批次空库位回填到本仓「未分配」
UPDATE inventory_stock_item si
JOIN inventory_storage_location l
  ON l.warehouse_id = si.warehouse_id AND l.location_name = '未分配'
SET si.location_id = l.location_id
WHERE si.location_id IS NULL;

-- 3) 汇总表唯一键：uk_inventory_item -> uk_inventory_item_location
-- 先加新键再删旧键：旧键可能被外键（inventory_stock.inventory_item_id）依赖，
-- 新键以 inventory_item_id 打头，可满足该外键后旧键才允许删除。
SET @has_new := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock' AND INDEX_NAME='uk_inventory_item_location');
SET @ddl := IF(@has_new=0, 'ALTER TABLE inventory_stock ADD UNIQUE KEY uk_inventory_item_location (inventory_item_id, location_id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_old := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock' AND INDEX_NAME='uk_inventory_item');
SET @ddl := IF(@has_old>0, 'ALTER TABLE inventory_stock DROP INDEX uk_inventory_item', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) 按「物料 + 库位」重建汇总（available_quantity 为生成列，不写入）
DELETE FROM inventory_stock;
INSERT INTO inventory_stock
  (inventory_item_id, material_id, material_code, material_name,
   total_quantity, total_reserved, earliest_expiry, location_id)
SELECT si.inventory_item_id, MAX(si.material_id), MAX(si.material_code), MAX(si.material_name),
       COALESCE(SUM(si.quantity),0), COALESCE(SUM(si.reserved_quantity),0),
       MIN(si.expiry_date), si.location_id
FROM inventory_stock_item si
WHERE si.status = 1
GROUP BY si.inventory_item_id, si.location_id;
