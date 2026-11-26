-- risk: low
-- P08 缺项补齐；结构交接 P02。任务码 dev-20261125-014。
-- 只新增可空字段，不回填或重写历史路线；执行前按现行规则校验用户手工备份。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='engineering_routing_item' AND COLUMN_NAME='default_plate_id')=0,
  'ALTER TABLE engineering_routing_item ADD COLUMN default_plate_id BIGINT NULL COMMENT ''工程建议网版ID，非实际用版''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='engineering_routing_item' AND COLUMN_NAME='print_requirements')=0,
  'ALTER TABLE engineering_routing_item ADD COLUMN print_requirements JSON NULL COMMENT ''固定印刷要求：位置、目数、张力N/cm''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='engineering_routing_item' AND INDEX_NAME='idx_routing_default_plate')=0,
  'ALTER TABLE engineering_routing_item ADD INDEX idx_routing_default_plate(default_plate_id)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
