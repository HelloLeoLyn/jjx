-- 276: S5 行级附件归属 —— 附件引用表加内部列 product_code（dev-20261013-043）
-- 目的：明细行上传的附件"归属某个产品"时，存"产品编码"（不建产品档案、不存 product_id）。
--       仅加一列内部字段（界面不可见），不新增任何数据行；产品详情「业务流转附件」按 product_code 精确聚合。
-- 幂等：列已存在则跳过；不改数据、不改索引、不改历史行。
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'sys_attachment_ref' AND column_name = 'product_code');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_attachment_ref ADD COLUMN product_code varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''产品编码（行级附件归属，内部字段，界面不可见）'' AFTER source_no',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
