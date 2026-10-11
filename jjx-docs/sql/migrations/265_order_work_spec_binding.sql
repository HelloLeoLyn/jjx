-- dev-20261011-011：销售采用版本与生产工单实际绑定版本
-- risk: low
-- 仅生成，由用户手工备份并执行；先执行发布版本迁移262。历史NULL不回填。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_order_product' AND COLUMN_NAME='work_spec_version_id'), 'SELECT 1', 'ALTER TABLE sales_order_product ADD COLUMN work_spec_version_id BIGINT NULL COMMENT ''采用作业规范发布版本；NULL未指定/历史未知''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='production_order' AND COLUMN_NAME='work_spec_version_id'), 'SELECT 1', 'ALTER TABLE production_order ADD COLUMN work_spec_version_id BIGINT NULL COMMENT ''实际绑定作业规范发布版本；NULL历史未知''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
