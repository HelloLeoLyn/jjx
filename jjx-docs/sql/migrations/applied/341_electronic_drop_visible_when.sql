-- 任务码：dev-20261104-008
-- 目的：修复迁移340遗留——删掉 component_type 后，「针数」字段仍带 visible_when={"component_type":"CONNECTOR"}，
--       引用不存在的字段会让模板校验报「属性显示条件引用无效：针数」，导入/保存全部被拦。
--       处理：去掉针数的显示条件，使其对所有电子元件子类都可见。
-- 幂等：无 visible_when 时不命中。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_REMOVE(attribute_schema,
        CONCAT(
            REPLACE(JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'pin_count', NULL, '$.fields[*].key')), '.key', ''),
            '.visible_when'))
WHERE template_code LIKE 'TPL_ELECTRONIC%'
  AND JSON_SEARCH(attribute_schema, 'one', 'pin_count', NULL, '$.fields[*].key') IS NOT NULL
  AND JSON_EXTRACT(attribute_schema,
        CONCAT(
            REPLACE(JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'pin_count', NULL, '$.fields[*].key')), '.key', ''),
            '.visible_when')) IS NOT NULL;

SELECT template_code,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].label')) AS 字段,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].visible_when')) AS 显示条件
FROM inventory_attribute_template WHERE template_code IN ('TPL_ELECTRONIC', 'TPL_ELECTRONIC_PIN', 'TPL_ELECTRONIC_HOUSING');
