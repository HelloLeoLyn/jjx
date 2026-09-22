-- dev-20260918-028：NCR 返工动作关联复检批，支持返工→报工→复检→结案闭环。
SET @exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_ncr_action'
    AND column_name = 'reinspection_lot_id'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_ncr_action ADD COLUMN reinspection_lot_id BIGINT NULL COMMENT ''返工完成后生成的FQC复检批'' AFTER rework_execution_id, ADD INDEX idx_ncr_action_reinspection_lot (reinspection_lot_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
