-- 264_attachment_sha256.sql
-- （原 261；与 codex 已提交的 261_sample_order_source 撞号，2026-10-11 dev-20261011-009 改号为 264）
-- dev-20261011-007（2026-10-11）：sys_attachment 加 sha256
-- 背景：产品作业规范发布版本要「引用不可变文件」，需服务端可信哈希 + 受控留存；
--       现有 sys_attachment 无哈希列（有 is_controlled/released，无需新增）。
-- 幂等：仅当列不存在时新增。无存量数据迁移（老附件 sha256 留空，按需补算）。
-- 回滚：ALTER TABLE sys_attachment DROP COLUMN sha256;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_attachment' AND COLUMN_NAME = 'sha256');
SET @sql := IF(@c = 0,
  'ALTER TABLE sys_attachment ADD COLUMN sha256 char(64) DEFAULT NULL COMMENT ''SHA-256(实际文件字节，服务端计算)'' AFTER file_size',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
