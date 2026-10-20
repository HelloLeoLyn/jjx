-- dev-20261020-034：BOM明细跳距；仅新增可空字段，不改历史业务数据。
-- risk: low
SET @split_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'split'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `split` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''跳距（含单位，如50mm）'' AFTER specification'
);
PREPARE split_stmt FROM @split_ddl;
EXECUTE split_stmt;
DEALLOCATE PREPARE split_stmt;
