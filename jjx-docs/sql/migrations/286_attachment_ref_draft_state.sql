-- dev-20261016-008 / -010（二阶段 S1）：附件引用表加「草稿键 + 引用状态」，并放开 source_id 可空。
-- 目的：未保存单据的附件先在库里占一条"草稿行"（draft_key 圈同一张草稿、ref_state=draft、source_id 空）；
--       单据保存时转正（补 source_id、ref_state=active）。依据 jjx-docs/reference/attachment-refactor-decisions-dev-20261016-001.md §4。
-- 幂等：均按 information_schema 判断，已存在则跳过。
-- 执行前：用户手工完成全库备份（排除 hr_employee）；按目标库 ops.schema.applied 核查未应用；用 scripts/db-migrate.sh 执行。

-- 1）source_id 放开为可空（草稿行还没有单据 ID）
SET @sql := (SELECT IF(
  (SELECT IS_NULLABLE FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='sys_attachment_ref' AND column_name='source_id') = 'YES',
  'SELECT ''skip: source_id already nullable''',
  'ALTER TABLE sys_attachment_ref MODIFY COLUMN source_id BIGINT NULL COMMENT ''来源对象ID；草稿行为空'''));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2）草稿键：同一张未保存草稿的附件共用一个键；转正后保留
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='sys_attachment_ref' AND column_name='draft_key'),
  'SELECT ''skip: draft_key exists''',
  'ALTER TABLE sys_attachment_ref ADD COLUMN draft_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''未保存单据草稿键（同草稿共键）；转正后保留'''));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3）引用状态：draft=未落定草稿 / active=正式
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='sys_attachment_ref' AND column_name='ref_state'),
  'SELECT ''skip: ref_state exists''',
  'ALTER TABLE sys_attachment_ref ADD COLUMN ref_state VARCHAR(16) NOT NULL DEFAULT ''active'' COMMENT ''引用状态：draft / active'''));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4）索引：按草稿键 + 状态圈附件（转正与到期清理都用）
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='sys_attachment_ref' AND index_name='idx_attachment_ref_draft'),
  'SELECT ''skip: idx_attachment_ref_draft exists''',
  'CREATE INDEX idx_attachment_ref_draft ON sys_attachment_ref (draft_key, ref_state)'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
