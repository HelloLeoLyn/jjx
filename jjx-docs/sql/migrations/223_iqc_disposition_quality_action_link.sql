-- risk: low
-- task: dev-20260928-022 / dev-20260928-023
-- 统一 IQC 处置事实与质量域处置动作的关联；不回填历史，不修改现有业务数据。

SET @sql := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'inventory_iqc_disposition_order'
            AND column_name = 'quality_action_id'),
  'SELECT 1',
  'ALTER TABLE inventory_iqc_disposition_order ADD COLUMN quality_action_id bigint NULL COMMENT ''关联 quality_ncr_action，审批/重试复用同一动作'' AFTER lot_id'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.statistics
          WHERE table_schema = DATABASE()
            AND table_name = 'inventory_iqc_disposition_order'
            AND index_name = 'idx_iqc_disposition_quality_action'),
  'SELECT 1',
  'ALTER TABLE inventory_iqc_disposition_order ADD KEY idx_iqc_disposition_quality_action (quality_action_id)'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
