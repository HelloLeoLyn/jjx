-- 任务码：dev-20261104-008
-- 目的：子类名称照原「元件类型」标签（用户口径 2026-11-04）：
--   33 RAW_AUX_ELECTRONIC_PIN     端子 → PIN
--   34 RAW_AUX_ELECTRONIC_HOUSING 塑体 → Housing
--   同时把模板里 component_type 选项标签改回 Housing（此前改成「塑体」）。
-- 其余 8 个子类名称本来与原标签一致，不动。
-- 幂等：重复执行结果一致。
-- 影响：分类名称/路径名与模板选项标签（配置数据），免备份（CONVENTIONS §2）。

UPDATE inventory_material_category
SET category_name = 'PIN', name_path = REPLACE(name_path, '/端子', '/PIN'), update_by = 'hermes', update_time = NOW()
WHERE category_code = 'RAW_AUX_ELECTRONIC_PIN';

UPDATE inventory_material_category
SET category_name = 'Housing', name_path = REPLACE(name_path, '/塑体', '/Housing'), update_by = 'hermes', update_time = NOW()
WHERE category_code = 'RAW_AUX_ELECTRONIC_HOUSING';

UPDATE inventory_attribute_template
SET attribute_schema = REPLACE(CAST(attribute_schema AS CHAR), '"label": "塑体"', '"label": "Housing"')
WHERE template_code LIKE 'TPL_ELECTRONIC%' AND CAST(attribute_schema AS CHAR) LIKE '%"label": "塑体"%';

UPDATE inventory_attribute_template
SET attribute_schema = REPLACE(CAST(attribute_schema AS CHAR), '"label":"塑体"', '"label":"Housing"')
WHERE template_code LIKE 'TPL_ELECTRONIC%' AND CAST(attribute_schema AS CHAR) LIKE '%"label":"塑体"%';

SELECT c.category_id, c.category_code, c.category_name, c.name_path, c.category_level
FROM inventory_material_category c WHERE c.parent_id = 29 ORDER BY c.sort_order, c.category_id;

SELECT template_code,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[0].options[*].label')) AS 元件类型选项
FROM inventory_attribute_template WHERE template_code LIKE 'TPL_ELECTRONIC%' ORDER BY template_id;
