-- dev-20260911-008：物料五类型 + 通用物料标签
-- 非破坏性迁移：新增油墨类型、物料标签分组/初始标签，隐藏旧材料分类菜单；不删除旧表或字段。

INSERT INTO sys_dict_item
    (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'inventory_material_type', 'INK', 'I', '油墨',
       '{"enum":"INK","origin":"MaterialEnums.java.Type"}', 4, 1,
       JSON_OBJECT('enum', 'INK', 'origin', 'MaterialEnums.java.Type'), 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item
    WHERE dict_code = 'inventory_material_type' AND item_value = 'I' AND deleted = 0 AND tenant_id = 1
);

INSERT INTO sys_dict_item
    (dict_code, item_key, item_value, label, remark, sort_order, is_active, deleted, tenant_id)
SELECT 'sys_tag_group', 'material_attribute', 'material_attribute', '物料属性',
       '物料档案通用标签分组', 2, 1, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item
    WHERE dict_code = 'sys_tag_group' AND item_key = 'material_attribute' AND deleted = 0 AND tenant_id = 1
);

INSERT INTO sys_tag
    (tag_code, tag_name, tag_group, parent_id, sort_order, status, remark, create_by, update_by, del_flag)
SELECT seed.tag_code, seed.tag_name, 'material_attribute', NULL, seed.sort_order, 1,
       'dev-20260911-008 初始物料标签', 'Codex', 'Codex', '0'
FROM (
    SELECT 'paper' tag_code, '纸张' tag_name, 1 sort_order UNION ALL
    SELECT 'film', '膜材', 2 UNION ALL
    SELECT 'adhesive', '胶水', 3 UNION ALL
    SELECT 'packaging', '包装材料', 4 UNION ALL
    SELECT 'water_based', '水性', 5 UNION ALL
    SELECT 'uv', 'UV', 6 UNION ALL
    SELECT 'eco_friendly', '环保', 7 UNION ALL
    SELECT 'food_grade', '食品级', 8 UNION ALL
    SELECT 'spot_color', '专色', 9
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_tag t
    WHERE t.tag_group = 'material_attribute' AND t.tag_code = seed.tag_code AND t.del_flag = '0'
);

UPDATE sys_menu
SET visible = '1', update_by = 'Codex', update_time = NOW(),
    remark = CONCAT(COALESCE(remark, ''), ' | dev-20260911-008：改用物料类型+通用标签，旧分类入口隐藏')
WHERE menu_id = 21
  AND component = 'views/inventory/material/category.vue'
  AND visible <> '1';
