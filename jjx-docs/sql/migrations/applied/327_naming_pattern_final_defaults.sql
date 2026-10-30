-- 任务码：dev-20261030-003
-- 目的：命名规则定稿默认——名称不含尺寸，顺序为 材质/品牌 → 型号 → 颜色 → 客供 → 样品。
-- 依据：尺寸已由规格模板表达；名称随裁切变化会破坏历史一致性；客供/样品为真才输出，放末尾不干扰识别。
-- 覆盖：直接重写 14 个物料品类的 name_pattern（含清空旧的每字段 separators，改用空格）。

-- 1) 膜类：材质 + 型号 + 颜色 + 客供 + 样品
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern', CAST(
  '{"fields":["meta","model","color","customersupplied","sampleonly"],"separator":" ","empty":"skip","separators":{}}' AS JSON))
WHERE template_code IN ('TPL_PET','TPL_PC','TPL_PVC','TPL_PMMA','TPL_OPTICAL','TPL_FOIL','TPL_BASE_OTHER','TPL_PROTECT');

-- 2) 胶带/泡棉/其他辅材/油墨：品牌 + 型号 + 颜色 + 客供 + 样品
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern', CAST(
  '{"fields":["brand","model","color","customersupplied","sampleonly"],"separator":" ","empty":"skip","separators":{}}' AS JSON))
WHERE template_code IN ('TPL_TAPE','TPL_FOAM','TPL_AUX_OTHER','TPL_INK');

-- 3) 离型材料：型号 + 颜色 + 客供 + 样品
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern', CAST(
  '{"fields":["model","color","customersupplied","sampleonly"],"separator":" ","empty":"skip","separators":{}}' AS JSON))
WHERE template_code = 'TPL_RELEASE';

-- 4) 电子元件：型号 + 颜色 + 客供 + 样品
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern', CAST(
  '{"fields":["model","color","customersupplied","sampleonly"],"separator":" ","empty":"skip","separators":{}}' AS JSON))
WHERE template_code = 'TPL_ELECTRONIC';

SELECT t.template_code, c.category_name,
       JSON_UNQUOTE(JSON_EXTRACT(t.attribute_schema, '$.name_pattern.fields')) AS 命名字段
FROM inventory_attribute_template t
LEFT JOIN inventory_material_category c ON c.category_id = t.category_id
ORDER BY t.template_id;
