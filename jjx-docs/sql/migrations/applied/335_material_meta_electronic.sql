-- 任务码：dev-20261104-004
-- 目的：材质有数据就必须记录（用户口径）
--   ① material_meta 字典新增 PHOSPHOR_BRONZE（磷青铜）、POLYCARBONATE（聚碳酸酯）——博明端子/塑体的真实材质；
--   ② 电子元件/辅料（TPL_ELECTRONIC）的 meta_options 由空改为这两项：候选为空时后端会拦任何材质
--      （"材质不在该品类允许范围"），所以过去只能写备注。
-- 幂等：字典用 ON DUPLICATE KEY；meta_options 用 JSON_SET 覆盖为固定两项，重复执行结果一致。
-- 影响：字典与模板配置，非表结构，免备份（CONVENTIONS §2）。

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, tenant_id)
SELECT 'material_meta', 'PHOSPHOR_BRONZE', 'PHOSPHOR_BRONZE', '磷青铜', IFNULL(MAX(sort_order), 0) + 1, 1, 1
FROM sys_dict_item WHERE dict_code = 'material_meta'
GROUP BY 'material_meta'
ON DUPLICATE KEY UPDATE label = VALUES(label), is_active = 1, deleted = 0;

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, tenant_id)
SELECT 'material_meta', 'POLYCARBONATE', 'POLYCARBONATE', '聚碳酸酯', IFNULL(MAX(sort_order), 0) + 1, 1, 1
FROM sys_dict_item WHERE dict_code = 'material_meta'
GROUP BY 'material_meta'
ON DUPLICATE KEY UPDATE label = VALUES(label), is_active = 1, deleted = 0;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.meta_options',
        JSON_ARRAY('POLYCARBONATE', 'PHOSPHOR_BRONZE'))
WHERE template_code = 'TPL_ELECTRONIC';

SELECT dict_code, item_value, label FROM sys_dict_item
WHERE dict_code = 'material_meta' AND item_value IN ('PHOSPHOR_BRONZE', 'POLYCARBONATE');

SELECT JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.meta_options')) AS 材质候选,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.meta_required')) AS 材质必填
FROM inventory_attribute_template WHERE template_code = 'TPL_ELECTRONIC';
