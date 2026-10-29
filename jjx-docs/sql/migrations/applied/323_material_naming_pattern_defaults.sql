-- 任务码：dev-20261029-018
-- 目的：给 14 个物料品类的属性模板灌一版默认命名规则（name_pattern），供界面在此基础上微调。
-- 口径（与 dev-20261028-005 讨论定稿一致）：
--   1) 形态词（膜/片/胶带）不入名称，靠材质+型号+尺寸+颜色表达；
--   2) 品牌原则上不入名称（渠道名进供应商/别名），仅胶带、泡棉保留品牌；
--   3) 型号自带厚度时不再重复勾厚度（故膜类用 size 复合字段，不单列 thickness_mm）。
-- 幂等：仅当 name_pattern 为空时写入，已有规则不覆盖。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["meta","model","size","color"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code IN ('TPL_PET','TPL_PC','TPL_PVC','TPL_PMMA','TPL_OPTICAL','TPL_FOIL')
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["meta","model","thickness_mm","width"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code = 'TPL_PROTECT'
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["brand","model","thickness_mm","width"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code IN ('TPL_TAPE','TPL_FOAM')
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["model","grammage","width","color"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code = 'TPL_RELEASE'
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["model","color"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code = 'TPL_ELECTRONIC'
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["meta","model","size","color"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code = 'TPL_BASE_OTHER'
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern',
  CAST('{"fields":["brand","model","color"],"separator":" ","empty":"skip"}' AS JSON))
WHERE template_code IN ('TPL_AUX_OTHER','TPL_INK')
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern') IS NULL;

SELECT t.template_code, c.category_name,
       JSON_UNQUOTE(JSON_EXTRACT(t.attribute_schema, '$.name_pattern.fields')) AS 命名字段
FROM inventory_attribute_template t
LEFT JOIN inventory_material_category c ON c.category_id = t.category_id
ORDER BY t.template_id;
