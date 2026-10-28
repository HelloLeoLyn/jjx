-- 任务码：dev-20261028-007
-- 目的：把物料必填规则从旧分类码（RM-FILM / AUX-ELEC 等 11 个）迁到现分类树的末级品类码，
--       使其真正能被 InventoryMaterialMapper.selectRequiredFields(category_code) 命中。
-- 背景：分类表 category_code 已是 RAW_BASE_PET 这类码，旧规则挂在 RM-FILM 等码上，永远匹配不到。
-- 映射：膜片类→基材四个品类+光学/扩散+金属箔；离型纸→离型材料；保护膜/双面胶→保护膜；
--       胶水/胶带→胶带胶膜；电子→电子元件；五金/包装/工具→其他辅材；油墨→油墨；其他基材→其他基材。
--       同一个新品类被多条旧规则命中时，required 取 OR（任一旧码必填即必填）。
-- 回滚：从 20261028-1115 全库备份恢复 inventory_material_required_field。

CREATE TEMPORARY TABLE legacy_required AS
SELECT category_code, field_key, required
FROM inventory_material_required_field
WHERE category_code IN ('RM-FILM','RM-PAPER','RM-ADHESIVE','RM-INK','AUX-ADHESIVE',
                        'AUX-ELEC','AUX-HARDWARE','AUX-PACK','AUX-TOOL','OTHER-A','OTHER-R');

DELETE FROM inventory_material_required_field
WHERE category_code IN ('RM-FILM','RM-PAPER','RM-ADHESIVE','RM-INK','AUX-ADHESIVE',
                        'AUX-ELEC','AUX-HARDWARE','AUX-PACK','AUX-TOOL','OTHER-A','OTHER-R');

INSERT INTO inventory_material_required_field (category_code, field_key, required)
SELECT mapped.new_code, legacy.field_key, MAX(legacy.required)
FROM legacy_required legacy
JOIN (
  SELECT 'RM-FILM' AS old_code, 'RAW_BASE_PET' AS new_code UNION ALL
  SELECT 'RM-FILM', 'RAW_BASE_PC' UNION ALL
  SELECT 'RM-FILM', 'RAW_BASE_PVC' UNION ALL
  SELECT 'RM-FILM', 'RAW_BASE_PMMA' UNION ALL
  SELECT 'RM-FILM', 'RAW_AUX_OPTICAL' UNION ALL
  SELECT 'RM-FILM', 'RAW_AUX_FOIL' UNION ALL
  SELECT 'RM-PAPER', 'RAW_AUX_RELEASE' UNION ALL
  SELECT 'RM-ADHESIVE', 'RAW_AUX_PROTECT' UNION ALL
  SELECT 'AUX-ADHESIVE', 'RAW_AUX_TAPE' UNION ALL
  SELECT 'AUX-ELEC', 'RAW_AUX_ELECTRONIC' UNION ALL
  SELECT 'AUX-HARDWARE', 'RAW_AUX_OTHER' UNION ALL
  SELECT 'AUX-PACK', 'RAW_AUX_FOAM' UNION ALL
  SELECT 'AUX-PACK', 'RAW_AUX_OTHER' UNION ALL
  SELECT 'AUX-TOOL', 'RAW_AUX_OTHER' UNION ALL
  SELECT 'RM-INK', 'RAW_INK' UNION ALL
  SELECT 'OTHER-R', 'RAW_BASE_OTHER'
) mapped ON mapped.old_code = legacy.category_code
GROUP BY mapped.new_code, legacy.field_key;

DROP TEMPORARY TABLE legacy_required;

SELECT category_code, COUNT(*) AS 规则数, SUM(required) AS 必填数
FROM inventory_material_required_field GROUP BY category_code ORDER BY category_code;

SELECT COUNT(*) AS 仍为旧码的行
FROM inventory_material_required_field
WHERE category_code NOT IN (SELECT category_code FROM inventory_material_category);
