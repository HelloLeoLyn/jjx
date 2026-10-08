-- dev-20260929-023
-- risk: low
-- 产品作业规范电子文档集 P0 补齐登记：sys_attachment 加「现行版」列 + 产品文件类别字典。
-- 背景：本任务 2026-09-29 由 OpenClaw 直接在库上完成改动（当时下班前做全库备份），未落迁移文件；
--       本文件按 CONVENTIONS §3 补登记，SQL 与本库已完成的实际变更完全一致。
-- 影响：仅新增 1 列 + 1 条字典类型 + 12 条字典项；不改/不删任何业务行。
-- 幂等：列已存在则跳过；字典行按唯一键 (dict_code,item_key[,deleted,tenant_id]) 存在即更新为同值。
-- 依赖：后端 SysAttachmentServiceImpl（读写 is_current）与前端 ProductFileLibrary.vue（读字典
--       product_file_category）必须与本列同批上线——列不存在时附件查询会报 Unknown column。
-- 收尾：本库改动已生效，执行本文件为「空跑 + 记账」；执行后 db-migrate 会把 244 记入已应用集合。

-- ── 1) sys_attachment.is_current（是否现行版）───────────────────────────────
SET @has_is_current = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'sys_attachment'
     AND column_name = 'is_current'
);
SET @ddl_is_current = IF(
  @has_is_current = 0,
  'ALTER TABLE sys_attachment ADD COLUMN is_current TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否现行版（产品文件库，dev-20260929-023）'' AFTER deleted',
  'SELECT 1'
);
PREPARE stmt_is_current FROM @ddl_is_current;
EXECUTE stmt_is_current;
DEALLOCATE PREPARE stmt_is_current;

-- ── 2) 字典类型 sys_dict ────────────────────────────────────────────────────
INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active, deleted, tenant_id)
VALUES ('product_file_category', '产品文件类别', 'product',
        '产品文件库/作业规范文件类别（dev-20260929-023）', 0, 1, 0, 1)
ON DUPLICATE KEY UPDATE
  dict_name  = VALUES(dict_name),
  dict_group = VALUES(dict_group),
  remark     = VALUES(remark),
  is_active  = VALUES(is_active);

-- ── 3) 字典项 sys_dict_item（12 项）────────────────────────────────────────
INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, deleted, tenant_id)
VALUES
  ('product_file_category', 'customer_supplied', '客供稿',       '客供稿',       1,  1, 0, 1),
  ('product_file_category', 'approval_doc',      '承认书',       '承认书',       2,  1, 0, 1),
  ('product_file_category', 'mould',             '模具',         '模具',         3,  1, 0, 1),
  ('product_file_category', 'confirm_drawing',   '确认图',       '确认图',       4,  1, 0, 1),
  ('product_file_category', 'film',              '菲林',         '菲林',         5,  1, 0, 1),
  ('product_file_category', 'spec',              '规范',         '规范',         6,  1, 0, 1),
  ('product_file_category', 'structure_drawing', '结构图',       '结构图',       7,  1, 0, 1),
  ('product_file_category', 'print_guide',       '印刷指导图',   '印刷指导图',   8,  1, 0, 1),
  ('product_file_category', 'product_atlas',     '产品图集',     '产品图集',     9,  1, 0, 1),
  ('product_file_category', 'sample_photo',      '样品照片',     '样品照片',     10, 1, 0, 1),
  ('product_file_category', 'sample_confirmed',  '客户确认样品', '客户确认样品', 11, 1, 0, 1),
  ('product_file_category', 'color_check',       '分色检查表',   '分色检查表',   12, 1, 0, 1)
ON DUPLICATE KEY UPDATE
  item_value = VALUES(item_value),
  label      = VALUES(label),
  sort_order = VALUES(sort_order),
  is_active  = VALUES(is_active);
