-- risk: low
-- task: dev-20260928-013 / dev-20260928-014
-- 只增加质量复检关系字段、入库明细更新时间及历史快照表，不回填伪历史、不修改现有业务数据。

SET @sql := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'quality_lot' AND column_name = 'relationship_mode'),
  'SELECT 1',
  'ALTER TABLE quality_lot ADD COLUMN relationship_mode varchar(16) NULL COMMENT ''REPLACE整批替代/INCREMENT局部追加'' AFTER version'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'quality_lot' AND column_name = 'scope_quantity'),
  'SELECT 1',
  'ALTER TABLE quality_lot ADD COLUMN scope_quantity decimal(18,4) NULL COMMENT ''本次复检明确针对数量'' AFTER relationship_mode'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'inventory_inbound_item' AND column_name = 'update_time'),
  'SELECT 1',
  'ALTER TABLE inventory_inbound_item ADD COLUMN update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER remark'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS quality_lot_history (
  history_id bigint NOT NULL AUTO_INCREMENT,
  lot_id bigint NOT NULL,
  event_type varchar(32) NOT NULL COMMENT 'ITEMS_SAVED/JUDGED/REVIEWED/DISPOSED',
  snapshot_json json NOT NULL,
  operator_name varchar(64) NULL,
  remark varchar(500) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (history_id),
  KEY idx_quality_lot_history_lot (lot_id, history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验批事实快照历史；旧数据不补造';
