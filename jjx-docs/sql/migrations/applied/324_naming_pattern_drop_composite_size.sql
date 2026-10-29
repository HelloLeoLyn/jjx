-- 任务码：dev-20261029-020
-- 目的：命名规则去掉复合字段 size，改用原始字段（宽/长/厚）。
-- 背景：可勾选列表同时有“尺寸(宽×长×厚)”与“宽/长/厚”，是同一组数据的两种写法；按要求只保留原始参数。
-- 影响面：仅重写用到 size 的 7 个品类模板的 name_pattern；其它规则不动。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["meta","model","width","length","thickness_mm","color"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code IN ('TPL_PET','TPL_PC','TPL_PVC','TPL_PMMA','TPL_OPTICAL','TPL_FOIL','TPL_BASE_OTHER');

SELECT t.template_code,
       JSON_UNQUOTE(JSON_EXTRACT(t.attribute_schema, '$.name_pattern.fields')) AS 命名字段
FROM inventory_attribute_template t
WHERE t.template_code IN ('TPL_PET','TPL_PC','TPL_PVC','TPL_PMMA','TPL_OPTICAL','TPL_FOIL','TPL_BASE_OTHER')
ORDER BY t.template_id;
