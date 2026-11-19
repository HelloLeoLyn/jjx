-- dev-20261119-004：复用库位表，保存真实上下级与用户选择的容量限制；不修改原库位名称/ID/库存。
-- risk: low
SET @schema_name = DATABASE();
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='inventory_storage_location' AND column_name='parent_id')=0,
 'ALTER TABLE inventory_storage_location ADD COLUMN parent_id BIGINT NULL COMMENT ''上级库位；空值为仓库下根位置'', ADD INDEX idx_location_parent(parent_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='inventory_storage_location' AND column_name='capacity_enabled')=0,
 'ALTER TABLE inventory_storage_location ADD COLUMN capacity_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''用户是否启用容量限制''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='inventory_storage_location' AND column_name='capacity_unit')=0,
 'ALTER TABLE inventory_storage_location ADD COLUMN capacity_unit VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''容量单位；仅同单位账量可比较''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
