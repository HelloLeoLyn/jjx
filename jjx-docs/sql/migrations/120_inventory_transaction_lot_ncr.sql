-- ============================================================================
-- 120: 库存流水关联检验批与不良台账 ——质量重构批1 / dev-20260917-004
-- 说明: 库存变动必须可追到 检验批（lot）与不良台账（ncr）；列可空，兼容历史流水。
--       差额入库与上限守卫的流程接入见 006/007。
-- ============================================================================
USE `jjx_erp_db`;

SET @exist_lot := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'inventory_transaction' AND column_name = 'lot_id');
SET @sql_lot := IF(@exist_lot = 0,
  'ALTER TABLE inventory_transaction ADD COLUMN lot_id BIGINT NULL COMMENT ''检验批ID'' AFTER source_no',
  'SELECT ''lot_id 已存在'' AS skip_check');
PREPARE stmt_lot FROM @sql_lot;
EXECUTE stmt_lot;
DEALLOCATE PREPARE stmt_lot;

SET @exist_ncr := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'inventory_transaction' AND column_name = 'ncr_id');
SET @sql_ncr := IF(@exist_ncr = 0,
  'ALTER TABLE inventory_transaction ADD COLUMN ncr_id BIGINT NULL COMMENT ''不良台账ID'' AFTER lot_id',
  'SELECT ''ncr_id 已存在'' AS skip_check');
PREPARE stmt_ncr FROM @sql_ncr;
EXECUTE stmt_ncr;
DEALLOCATE PREPARE stmt_ncr;

SET @exist_idx := (SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'inventory_transaction' AND index_name = 'idx_tx_lot');
SET @sql_idx := IF(@exist_idx = 0,
  'ALTER TABLE inventory_transaction ADD INDEX idx_tx_lot (lot_id)',
  'SELECT ''idx_tx_lot 已存在'' AS skip_check');
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

SELECT '120 库存流水关联列就绪' AS check_point;
SHOW COLUMNS FROM inventory_transaction LIKE 'lot_id';
SHOW COLUMNS FROM inventory_transaction LIKE 'ncr_id';
