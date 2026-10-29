-- 任务码：dev-20261029-031
-- 目的：按品类给一版默认规格模板（厚*宽*长，属性由操作员再勾）。
-- 口径：尺寸之间用 *，单位跟随各字段；不显示字段名；未配置的品类保持旧拼接。
-- 幂等：仅当 spec_pattern 为空时写入。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.spec_pattern', CAST(
  '{"fields":[{"key":"thickness_mm","sep":""},{"key":"width","sep":"*"},{"key":"length","sep":"*"}],"show_label":false,"with_unit":true}' AS JSON))
WHERE template_code IN ('TPL_PET','TPL_PC','TPL_PVC','TPL_PMMA','TPL_OPTICAL','TPL_FOIL','TPL_BASE_OTHER')
  AND JSON_EXTRACT(attribute_schema, '$.spec_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.spec_pattern', CAST(
  '{"fields":[{"key":"thickness_mm","sep":""},{"key":"width","sep":"*"}],"show_label":false,"with_unit":true}' AS JSON))
WHERE template_code IN ('TPL_PROTECT','TPL_TAPE','TPL_FOAM')
  AND JSON_EXTRACT(attribute_schema, '$.spec_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.spec_pattern', CAST(
  '{"fields":[{"key":"width","sep":""},{"key":"length","sep":"*"}],"show_label":false,"with_unit":true}' AS JSON))
WHERE template_code = 'TPL_RELEASE'
  AND JSON_EXTRACT(attribute_schema, '$.spec_pattern') IS NULL;

SELECT t.template_code, c.category_name,
       JSON_UNQUOTE(JSON_EXTRACT(t.attribute_schema, '$.spec_pattern')) AS 规格模板
FROM inventory_attribute_template t
LEFT JOIN inventory_material_category c ON c.category_id = t.category_id
ORDER BY t.template_id;
