-- 任务码：dev-20261030-012
-- 目的：修正保护膜命名规则里已删除的 grid 字段（330 已并入 surface_treatment），否则保存/预览报“命名规则含未知字段：grid”。
-- 处理：按现有顺序把 grid 换成 surface_treatment（若已存在则只去重），其余字段与分隔符不变。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern.fields',
  CAST('["brand","meta","category","model","color","customersupplied","sampleonly","surface_treatment","adhesiveness"]' AS JSON))
WHERE template_code = 'TPL_PROTECT'
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern.fields') IS NOT NULL;

SELECT t.template_code,
       JSON_UNQUOTE(JSON_EXTRACT(t.attribute_schema, '$.name_pattern.fields')) AS 命名规则字段
FROM inventory_attribute_template t WHERE t.template_code = 'TPL_PROTECT';
