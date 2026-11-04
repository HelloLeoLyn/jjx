-- 任务码：dev-20261104-002
-- 目的：物料导入前置配置（用户口径 2A + 3A）
--   ① 单位字典新增 PIN（针）——端子按针计量，与 PCS（件）区分；
--   ② 品牌字典新增 博明（BOMING）——博明.xlsx 全部物料的品牌；
--   ③ 电子元件/辅料（TPL_ELECTRONIC）模板新增 包装数量 pack_qty(整数) 与 包装单位 pack_unit(文本)，
--      承载 20000PIN/盘、1000PCS/包 这类包装信息，不再塞进规格或尺寸。
-- 幂等：字典用 NOT EXISTS 守卫；模板字段用 JSON_SEARCH 守卫，重复执行影响 0 行。
-- 影响：字典与模板配置（非表结构），免备份（CONVENTIONS §2）。

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, tenant_id)
SELECT 'unit', 'master_PIN', 'PIN', '针', IFNULL(MAX(sort_order), 0) + 1, 1, 1
FROM sys_dict_item
WHERE dict_code = 'unit'
GROUP BY 'unit'
ON DUPLICATE KEY UPDATE label = VALUES(label), is_active = 1, deleted = 0;

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, tenant_id)
SELECT 'brand', 'BOMING', 'BOMING', '博明', IFNULL(MAX(sort_order), 0) + 1, 1, 1
FROM sys_dict_item
WHERE dict_code = 'brand'
GROUP BY 'brand'
ON DUPLICATE KEY UPDATE label = VALUES(label), is_active = 1, deleted = 0;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields',
        JSON_ARRAY_APPEND(JSON_EXTRACT(attribute_schema, '$.fields'), '$',
            JSON_OBJECT('key', 'pack_qty', 'label', '包装数量', 'type', 'integer', 'required', FALSE)))
WHERE template_code = 'TPL_ELECTRONIC'
  AND JSON_SEARCH(attribute_schema, 'one', 'pack_qty', NULL, '$.fields[*].key') IS NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields',
        JSON_ARRAY_APPEND(JSON_EXTRACT(attribute_schema, '$.fields'), '$',
            JSON_OBJECT('key', 'pack_unit', 'label', '包装单位', 'type', 'string', 'required', FALSE)))
WHERE template_code = 'TPL_ELECTRONIC'
  AND JSON_SEARCH(attribute_schema, 'one', 'pack_unit', NULL, '$.fields[*].key') IS NULL;

SELECT dict_code, item_value, label FROM sys_dict_item
WHERE (dict_code = 'unit' AND item_value = 'PIN') OR (dict_code = 'brand' AND item_value = 'BOMING');

SELECT JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].label')) AS 字段,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].key')) AS 编码
FROM inventory_attribute_template WHERE template_code = 'TPL_ELECTRONIC';
