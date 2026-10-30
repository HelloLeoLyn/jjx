-- 任务码：dev-20261029-032
-- 目的：规格模板单位口径统一为“只在末尾带一次单位”（300*400mm），避免 300mm*400mm。
-- 影响面：仅已配置 spec_pattern 的模板追加 unit_position='end'；未配置的不动。
-- 幂等：已有 unit_position 的不覆盖。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.spec_pattern.unit_position', 'end')
WHERE JSON_EXTRACT(attribute_schema, '$.spec_pattern') IS NOT NULL
  AND JSON_EXTRACT(attribute_schema, '$.spec_pattern.unit_position') IS NULL;

SELECT t.template_code, JSON_UNQUOTE(JSON_EXTRACT(t.attribute_schema, '$.spec_pattern.unit_position')) AS 单位位置
FROM inventory_attribute_template t
WHERE JSON_EXTRACT(t.attribute_schema, '$.spec_pattern') IS NOT NULL
ORDER BY t.template_id;
