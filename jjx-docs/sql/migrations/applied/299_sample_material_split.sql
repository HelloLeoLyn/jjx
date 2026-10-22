-- dev-20261022-004：样品独立BOM跳距沿用标准BOM split字段，单位mm；可空，不改变历史用量。
-- 新增事实字段，无回填、不修改历史记录。通过db-migrate.sh和用户手工备份执行。
SET @sample_split_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'sales_sample_bom' AND column_name = 'split');
SET @sample_split_ddl = IF(@sample_split_exists = 0,
 'ALTER TABLE sales_sample_bom ADD COLUMN split VARCHAR(50) NULL COMMENT ''跳距（整数mm，可空）'' AFTER module_qty',
 'SELECT ''sales_sample_bom.split already exists''');
PREPARE sample_split_stmt FROM @sample_split_ddl;
EXECUTE sample_split_stmt;
DEALLOCATE PREPARE sample_split_stmt;
