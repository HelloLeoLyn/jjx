-- ============================================================================
-- 121: 末道（成品）工序标记 ——质量重构批2 / dev-20260917-006
-- 口径: 谁能起成品检验由"工程标记的末道工序"决定；未标记时退化为"顺序最大"并提示。
--   ① engineering_standard_process（工序主数据，工程维护）
--   ② engineering_routing_item（路线行可覆盖）
--   ③ production_operation_execution（执行上冗余，便于运行时判定）
-- 幂等: 列已存在则跳过。
-- ============================================================================
USE `jjx_erp_db`;

SET @exist_sp := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND column_name = 'is_final_process');
SET @sql := IF(@exist_sp = 0,
  'ALTER TABLE engineering_standard_process ADD COLUMN is_final_process TINYINT NOT NULL DEFAULT 0 COMMENT ''是否末道(成品)工序 工程标记''',
  'SELECT ''standard_process.is_final_process 已存在'' AS skip_check');
PREPARE s1 FROM @sql; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @exist_ri := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'engineering_routing_item' AND column_name = 'is_final_process');
SET @sql := IF(@exist_ri = 0,
  'ALTER TABLE engineering_routing_item ADD COLUMN is_final_process TINYINT NOT NULL DEFAULT 0 COMMENT ''是否末道(成品)工序（覆盖主数据）''',
  'SELECT ''routing_item.is_final_process 已存在'' AS skip_check');
PREPARE s2 FROM @sql; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @exist_ex := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'production_operation_execution' AND column_name = 'is_final_process');
SET @sql := IF(@exist_ex = 0,
  'ALTER TABLE production_operation_execution ADD COLUMN is_final_process TINYINT NOT NULL DEFAULT 0 COMMENT ''是否末道(成品)工序''',
  'SELECT ''execution.is_final_process 已存在'' AS skip_check');
PREPARE s3 FROM @sql; EXECUTE s3; DEALLOCATE PREPARE s3;

SELECT '121 末道工序标记列就绪' AS check_point;
