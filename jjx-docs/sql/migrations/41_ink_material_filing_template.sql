-- dev-20260902-104：油墨物料建档模板（仅供人工确认后执行，本迁移默认不执行数据写入）
--
-- 仓库现状：物料新增要求调用方提供 material_code；现有自动编码仅用于 Excel 导入，
-- 且固定生成 MTRyyyyMMddNNNN。为避免改变全域物料编码，本期采用人工建档 SQL 模板，
-- 油墨编码统一为 INK-xxx（三位流水，例如 INK-001）。
--
-- 执行前要求：
-- 1. Leo/工程部确认物料名称、规格、单位及编码；
-- 2. 确认 inventory_material_category.category_code='INK'；
-- 3. 替换下方占位内容并解除注释；禁止用本脚本批量改挂现有 R 物料分类。

-- START TRANSACTION;

-- INSERT INTO inventory_material (
--   material_code, material_name, material_type, process_group, category_id,
--   specification, unit, batch_control, safe_stock, max_stock, reorder_point,
--   status, create_by, update_by, remark
-- )
-- SELECT
--   'INK-001', '待确认油墨名称', 'R', 'M', category_id,
--   '待确认规格', 'KG', 1, 0, 0, 0,
--   1, 'manual', 'manual', 'dev-20260902-104：经工程部确认后建档'
-- FROM inventory_material_category
-- WHERE category_code = 'INK'
--   AND NOT EXISTS (
--     SELECT 1 FROM inventory_material WHERE material_code = 'INK-001'
--   );

-- 如需将已人工确认的油墨物料改为 INK-xxx，只允许按明确 material_id 单条执行：
-- UPDATE inventory_material
-- SET material_code = 'INK-002', update_by = 'manual'
-- WHERE material_id = 0 -- 替换为已确认 ID
--   AND NOT EXISTS (
--     SELECT 1 FROM (SELECT material_id FROM inventory_material WHERE material_code = 'INK-002') duplicate_code
--   );

-- COMMIT;
