-- dev-20261011-030：同内容附件只读检测索引；非唯一，不合并文件、不改删除/受控规则。
-- 范围：产品为 biz_type=product + biz_id=产品ID；其他为 biz_type + biz_id。
-- 存量 sha256=NULL 不回填；回滚：ALTER TABLE sys_attachment DROP INDEX idx_attachment_hash_scope;
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment'
    AND INDEX_NAME='idx_attachment_hash_scope');
SET @sql := IF(@exists=0,
  'ALTER TABLE sys_attachment ADD INDEX idx_attachment_hash_scope (sha256, biz_type, biz_id, deleted)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
