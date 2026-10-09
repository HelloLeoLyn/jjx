-- risk: low
-- dev-20261009-023：图纸/工程文件「受控 + 下发」——复用 sys_attachment（产品文件库），不建新表。
-- 幂等：列已存在则跳过。
SET @db := DATABASE();
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='is_controlled')=0,
  'ALTER TABLE sys_attachment ADD COLUMN is_controlled TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否受控（工程图/技术文档）'' AFTER is_current','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='released')=0,
  'ALTER TABLE sys_attachment ADD COLUMN released TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否已下发'' AFTER is_controlled','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='released_at')=0,
  'ALTER TABLE sys_attachment ADD COLUMN released_at DATETIME NULL COMMENT ''下发时间'' AFTER released','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='released_by')=0,
  'ALTER TABLE sys_attachment ADD COLUMN released_by VARCHAR(64) NULL COMMENT ''下发人'' AFTER released_at','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME IN ('is_controlled','released','released_at','released_by');
