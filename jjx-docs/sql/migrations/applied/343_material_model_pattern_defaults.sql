-- 任务码：dev-20261105-015
-- risk: low
-- 草案：仅提交，迁移 343 未执行；须由用户备份后通过 db-migrate.sh 执行。
-- PET膜/片、PC膜/片：材质代码 + 厚度μm（例 PET125 / PC254）。
-- 只配置新建物料的默认型号规则，不改表结构、不更新任何存量物料型号。
-- 幂等：仅补缺失或 JSON null 的 model_pattern；保留已有规则（含主动清空的 fields）。

UPDATE inventory_attribute_template t
JOIN inventory_material_category c ON c.category_id = t.category_id
SET t.attribute_schema = JSON_SET(t.attribute_schema, '$.model_pattern', CAST(
  '{"fields":["meta","thickness_mm"],"separator":" ","separators":{"thickness_mm":""},"empty":"skip","units":{"thickness_mm":"μm"}}' AS JSON))
WHERE c.category_code IN ('RAW_BASE_PET', 'RAW_BASE_PC')
  AND t.template_code IN ('TPL_PET', 'TPL_PC')
  AND (JSON_EXTRACT(t.attribute_schema, '$.model_pattern') IS NULL
       OR JSON_TYPE(JSON_EXTRACT(t.attribute_schema, '$.model_pattern')) = 'NULL');

SELECT t.template_code, c.category_name,
       JSON_EXTRACT(t.attribute_schema, '$.model_pattern') AS model_pattern
FROM inventory_attribute_template t
JOIN inventory_material_category c ON c.category_id = t.category_id
WHERE c.category_code IN ('RAW_BASE_PET', 'RAW_BASE_PC');
