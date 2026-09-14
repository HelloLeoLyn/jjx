-- dev-20260912-013：标准工序配置是否常规携带实例作业说明。
-- 具体说明仍保存在打样/路线/历史草稿实例中，不写回标准工序主数据。
SET @has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'engineering_standard_process'
    AND column_name = 'has_work_instruction'
);
SET @ddl := IF(
  @has_column = 0,
  'ALTER TABLE engineering_standard_process ADD COLUMN has_work_instruction tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否常规携带作业说明：0否1是'' AFTER has_index',
  'SELECT ''engineering_standard_process.has_work_instruction already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
