-- 任务码：dev-20261104-008
-- 目的：电子元件/辅料的子类补齐到「原元件类型」的 10 类（用户口径 2026-11-04：参考之前的元件类型）。
--   已存在：LED / 端子(PIN) / 塑体(HOUSING)；本次补：连接器 CONNECTOR、电阻 RESISTOR、电容 CAPACITOR、
--   二极管 DIODE、弹片 SPRING、五金件 HARDWARE、其他 OTHER。
--   每个新子类各建一条模板（schema 复制自 TPL_ELECTRONIC），命名 TPL_ELECTRONIC_<CODE>。
-- 幂等：分类/模板均用 NOT EXISTS 守卫，重复执行影响 0 行。
-- 影响：分类与模板配置，非表结构，免备份（CONVENTIONS §2）。

INSERT INTO inventory_material_category
    (category_code, category_name, parent_id, category_level, category_path, name_path, sort_order, status, create_by, create_time, update_by, update_time)
SELECT v.code, v.name, p.category_id, p.category_level + 1,
       CONCAT(p.category_path, '/', v.code), CONCAT(p.name_path, '/', v.name), v.sort_order, '1', 'hermes', NOW(), 'hermes', NOW()
FROM inventory_material_category p
JOIN (
    SELECT 'RAW_AUX_ELECTRONIC_CONNECTOR' AS code, '连接器' AS name, 4 AS sort_order
    UNION ALL SELECT 'RAW_AUX_ELECTRONIC_RESISTOR', '电阻', 5
    UNION ALL SELECT 'RAW_AUX_ELECTRONIC_CAPACITOR', '电容', 6
    UNION ALL SELECT 'RAW_AUX_ELECTRONIC_DIODE', '二极管', 7
    UNION ALL SELECT 'RAW_AUX_ELECTRONIC_SPRING', '弹片', 8
    UNION ALL SELECT 'RAW_AUX_ELECTRONIC_HARDWARE', '五金件', 9
    UNION ALL SELECT 'RAW_AUX_ELECTRONIC_OTHER', '其他', 10
) v
WHERE p.category_id = 29
  AND NOT EXISTS (SELECT 1 FROM inventory_material_category x WHERE x.category_code = v.code);

INSERT INTO inventory_attribute_template (template_code, template_name, category_id, attribute_schema, status, create_by, update_by, create_time, update_time)
SELECT CONCAT('TPL_', c.category_code), CONCAT('电子元件/辅料 · ', c.category_name), c.category_id, t.attribute_schema, 1, 'hermes', 'hermes', NOW(), NOW()
FROM inventory_material_category c
JOIN inventory_attribute_template t ON t.template_code = 'TPL_ELECTRONIC'
WHERE c.category_code IN (
        'RAW_AUX_ELECTRONIC_CONNECTOR', 'RAW_AUX_ELECTRONIC_RESISTOR', 'RAW_AUX_ELECTRONIC_CAPACITOR',
        'RAW_AUX_ELECTRONIC_DIODE', 'RAW_AUX_ELECTRONIC_SPRING', 'RAW_AUX_ELECTRONIC_HARDWARE',
        'RAW_AUX_ELECTRONIC_OTHER')
  AND NOT EXISTS (SELECT 1 FROM inventory_attribute_template x WHERE x.category_id = c.category_id);

-- 模板编码统一成 TPL_ELECTRONIC_<子类短码>（上面用父级品类编码拼出来过长）
UPDATE inventory_attribute_template
SET template_code = REPLACE(template_code, 'TPL_RAW_AUX_ELECTRONIC_', 'TPL_ELECTRONIC_')
WHERE category_id IN (SELECT category_id FROM inventory_material_category WHERE parent_id = 29);

SELECT c.category_id, c.category_code, c.category_name, c.category_level, t.template_code, t.status AS tpl_status
FROM inventory_material_category c
LEFT JOIN inventory_attribute_template t ON t.category_id = c.category_id
WHERE c.parent_id = 29 ORDER BY c.sort_order, c.category_id;
