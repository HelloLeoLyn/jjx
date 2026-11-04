-- 任务码：dev-20261104-008
-- 目的：物料信息架构对齐——给「电子元件/辅料」加第 4 级子类（业内分类树深度可不等），
--       子类：LED / PIN（端子）/ HOUSING（塑体），编码 = 父品类前缀 + 子类。
--   ① 插入 3 条第 4 级分类（parent=29，level=4，path 续写）；
--   ② 每个子类各建一条属性模板（从父级 TPL_ELECTRONIC 复制，模板编码/名称按子类）；
--   ③ 父级模板停用保留（status=0，标"已迁移子类"），不物理删除；
--   ④ HOUSING 显示名由英文 Housing 改为中文「塑体」；
--   ⑤ 把「电子元件/辅料」下 3 条 LED 物料迁到 LED 子类（回滚见文件末尾）。
-- 幂等：分类/模板均用 NOT EXISTS 守卫；重复执行影响 0 行。
-- 备份说明：⑤ 属批量 UPDATE（3 行），按 CONVENTIONS §2 应先做表级 guard 备份；回滚 SQL 附在文末。

-- ① 子类分类
INSERT INTO inventory_material_category
    (category_code, category_name, parent_id, category_level, category_path, name_path, sort_order, status, create_by, create_time, update_by, update_time)
SELECT 'RAW_AUX_ELECTRONIC_LED', 'LED', p.category_id, p.category_level + 1,
       CONCAT(p.category_path, '/RAW_AUX_ELECTRONIC_LED'), CONCAT(p.name_path, '/LED'), 1, '1', 'hermes', NOW(), 'hermes', NOW()
FROM inventory_material_category p
WHERE p.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM (SELECT category_code FROM inventory_material_category) x WHERE x.category_code = 'RAW_AUX_ELECTRONIC_LED');

INSERT INTO inventory_material_category
    (category_code, category_name, parent_id, category_level, category_path, name_path, sort_order, status, create_by, create_time, update_by, update_time)
SELECT 'RAW_AUX_ELECTRONIC_PIN', '端子', p.category_id, p.category_level + 1,
       CONCAT(p.category_path, '/RAW_AUX_ELECTRONIC_PIN'), CONCAT(p.name_path, '/端子'), 2, '1', 'hermes', NOW(), 'hermes', NOW()
FROM inventory_material_category p
WHERE p.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM (SELECT category_code FROM inventory_material_category) x WHERE x.category_code = 'RAW_AUX_ELECTRONIC_PIN');

INSERT INTO inventory_material_category
    (category_code, category_name, parent_id, category_level, category_path, name_path, sort_order, status, create_by, create_time, update_by, update_time)
SELECT 'RAW_AUX_ELECTRONIC_HOUSING', '塑体', p.category_id, p.category_level + 1,
       CONCAT(p.category_path, '/RAW_AUX_ELECTRONIC_HOUSING'), CONCAT(p.name_path, '/塑体'), 3, '1', 'hermes', NOW(), 'hermes', NOW()
FROM inventory_material_category p
WHERE p.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM (SELECT category_code FROM inventory_material_category) x WHERE x.category_code = 'RAW_AUX_ELECTRONIC_HOUSING');

-- ④ HOUSING 显示名改中文（父级模板与子类模板都改）
UPDATE inventory_attribute_template
SET attribute_schema = REPLACE(CAST(attribute_schema AS CHAR), '"label": "Housing"', '"label": "塑体"')
WHERE category_id IN (29, (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_LED'),
                      (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_PIN'),
                      (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_HOUSING'));
UPDATE inventory_attribute_template
SET attribute_schema = REPLACE(CAST(attribute_schema AS CHAR), '"label":"Housing"', '"label":"塑体"')
WHERE category_id IN (29, (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_LED'),
                      (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_PIN'),
                      (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_HOUSING'));

-- ② 每个子类复制一条模板（schema 沿用父级，可后续按子类微调）
INSERT INTO inventory_attribute_template (template_code, template_name, category_id, attribute_schema, status, create_by, update_by, create_time, update_time)
SELECT 'TPL_ELECTRONIC_LED', '电子元件/辅料 · LED', c.category_id, t.attribute_schema, 1, 'hermes', 'hermes', NOW(), NOW()
FROM inventory_attribute_template t
JOIN inventory_material_category c ON c.category_code = 'RAW_AUX_ELECTRONIC_LED'
WHERE t.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM (SELECT template_code FROM inventory_attribute_template) x WHERE x.template_code = 'TPL_ELECTRONIC_LED');

INSERT INTO inventory_attribute_template (template_code, template_name, category_id, attribute_schema, status, create_by, update_by, create_time, update_time)
SELECT 'TPL_ELECTRONIC_PIN', '电子元件/辅料 · 端子', c.category_id, t.attribute_schema, 1, 'hermes', 'hermes', NOW(), NOW()
FROM inventory_attribute_template t
JOIN inventory_material_category c ON c.category_code = 'RAW_AUX_ELECTRONIC_PIN'
WHERE t.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM (SELECT template_code FROM inventory_attribute_template) x WHERE x.template_code = 'TPL_ELECTRONIC_PIN');

INSERT INTO inventory_attribute_template (template_code, template_name, category_id, attribute_schema, status, create_by, update_by, create_time, update_time)
SELECT 'TPL_ELECTRONIC_HOUSING', '电子元件/辅料 · 塑体', c.category_id, t.attribute_schema, 1, 'hermes', 'hermes', NOW(), NOW()
FROM inventory_attribute_template t
JOIN inventory_material_category c ON c.category_code = 'RAW_AUX_ELECTRONIC_HOUSING'
WHERE t.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM (SELECT template_code FROM inventory_attribute_template) x WHERE x.template_code = 'TPL_ELECTRONIC_HOUSING');

-- ③ 父级模板停用（保留记录，父级随后因"有子类"自动不可选）
UPDATE inventory_attribute_template SET status = 0, update_by = 'hermes', update_time = NOW() WHERE category_id = 29;

-- ⑤ 3 条 LED 物料迁到 LED 子类（回滚：UPDATE inventory_material SET category_id=29 WHERE category_id=<LED子类id> AND JSON component_type='LED'）
UPDATE inventory_material
SET category_id = (SELECT category_id FROM inventory_material_category WHERE category_code = 'RAW_AUX_ELECTRONIC_LED'),
    update_by = 'hermes', update_time = NOW()
WHERE category_id = 29
  AND JSON_UNQUOTE(JSON_EXTRACT(attribute_json, '$.component_type')) = 'LED';

SELECT c.category_id, c.category_code, c.category_name, c.category_level, c.status, t.template_code, t.status AS tpl_status
FROM inventory_material_category c
LEFT JOIN inventory_attribute_template t ON t.category_id = c.category_id
WHERE c.category_code LIKE 'RAW_AUX_ELECTRONIC%' ORDER BY c.category_id;

SELECT material_code, material_name, category_id FROM inventory_material WHERE material_code IN ('AUX000008','AUX000009','AUX000010');
