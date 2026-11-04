-- 任务码：dev-20261104-007
-- 目的：材质口径更正（用户口径 2026-11-04）——材质就用 PC（行业通用缩写），不要 POLYCARBONATE、也不要中文名"聚碳酸酯"。
--   ① 停用上一步误加的 material_meta/POLYCARBONATE（保留审计，不物理删除）；
--   ② 电子元件/辅料的材质候选改为 [PC, PHOSPHOR_BRONZE]（端子磷青铜、塑体 PC）。
-- 幂等：重复执行结果一致。
-- 影响：字典与模板配置，非表结构，免备份（CONVENTIONS §2）。

UPDATE sys_dict_item
SET is_active = 0
WHERE dict_code = 'material_meta' AND item_value = 'POLYCARBONATE';

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.meta_options', JSON_ARRAY('PC', 'PHOSPHOR_BRONZE'))
WHERE template_code = 'TPL_ELECTRONIC';

SELECT dict_code, item_value, label, is_active FROM sys_dict_item
WHERE dict_code = 'material_meta' AND item_value IN ('PC', 'POLYCARBONATE', 'PHOSPHOR_BRONZE');

SELECT JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.meta_options')) AS 材质候选
FROM inventory_attribute_template WHERE template_code = 'TPL_ELECTRONIC';
