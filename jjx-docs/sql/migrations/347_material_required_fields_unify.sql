-- 任务码：dev-20261105-020（品类必填字段可配 + 统一口径草案）
-- risk: high（重灌全部现有品类的公共字段必填配置，不改结构、不动物料/模板/前缀）
-- 迁移 347 未执行。由用户手工备份后通过 scripts/db-migrate.sh 执行。
-- 唯一真源仍为 inventory_material_required_field；界面保存后可按实际业务调整。
-- 五个 key 全量 upsert（含 required=0），不删除行；重复执行配置结果不变。
-- 所有品类：材料显示名/颜色/规格必填；胶带、保护膜、离型膜/纸、电子元件型号必填。
-- 品牌：胶带与电子元件必填；保护膜按任务书末条设为非必填；离型膜/纸延续 346 非必填。
-- 本脚本会重新应用草案口径；上线后不要用它覆盖用户在界面调整的配置。
INSERT INTO inventory_material_required_field (category_code, field_key, required)
SELECT c.category_code, f.field_key,
       CASE
         WHEN f.field_key IN ('material_grade', 'color', 'specification') THEN 1
         WHEN f.field_key = 'model' AND (
           c.category_code IN ('RAW_AUX_TAPE', 'RAW_AUX_PROTECT', 'RAW_AUX_RELEASE_FILM', 'RAW_AUX_RELEASE_PAPER', 'RAW_AUX_ELECTRONIC')
           OR c.category_code LIKE 'RAW!_AUX!_ELECTRONIC!_%' ESCAPE '!'
         ) THEN 1
         WHEN f.field_key = 'brand' AND (
           c.category_code IN ('RAW_AUX_TAPE', 'RAW_AUX_ELECTRONIC')
           OR c.category_code LIKE 'RAW!_AUX!_ELECTRONIC!_%' ESCAPE '!'
         ) THEN 1
         ELSE 0
       END AS required
FROM inventory_material_category c
CROSS JOIN (
  SELECT 'material_grade' AS field_key UNION ALL SELECT 'model' UNION ALL
  SELECT 'color' UNION ALL SELECT 'specification' UNION ALL SELECT 'brand'
) f
ON DUPLICATE KEY UPDATE required = VALUES(required);
