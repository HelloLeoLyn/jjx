-- risk: low
-- dev-20261009-024：工程图纸入口名称、具体图种字典。
-- 仅菜单单行改名与幂等字典新增；无DDL，不改附件类别/路径，不改角色授权。
-- 由用户部署时执行；完成后刷新字典缓存、重新登录刷新菜单。
START TRANSACTION;

SET @drawing_name_menu = (
    SELECT menu_id FROM sys_menu WHERE perms = 'engineering:drawing:view' LIMIT 1
);
UPDATE sys_menu
SET menu_name = '工程图纸', update_by = 'codex', update_time = NOW()
WHERE menu_id = @drawing_name_menu AND menu_name = '图纸管理';

INSERT INTO sys_dict
    (dict_code,dict_name,dict_group,remark,sort_order,is_active,deleted,tenant_id)
SELECT 'product_file_category','产品文件类别','product','产品文件及工程图纸的图种分类',0,1,0,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'product_file_category');

INSERT INTO sys_dict_item
    (dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'product_file_category',k.item_key,k.item_value,k.item_value,k.sort_order,1,0,1
FROM (
    SELECT 'outline_drawing' AS item_key,'外形尺寸图' AS item_value,13 AS sort_order
    UNION ALL SELECT 'panel_drawing','面板图',14
    UNION ALL SELECT 'circuit_drawing','线路图',15
    UNION ALL SELECT 'assembly_drawing','组装图',16
    UNION ALL SELECT 'packaging_drawing','包装图',17
    UNION ALL SELECT 'other_engineering_drawing','其他工程图',18
) k
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item d
    WHERE d.dict_code = 'product_file_category'
      AND (d.item_key = k.item_key OR d.item_value = k.item_value)
);

-- 历史 item_value='产品图集' 保留；前端只读兼容展示，不再提供此值用于新上传。
COMMIT;
SELECT menu_id,menu_name,perms FROM sys_menu WHERE menu_id = @drawing_name_menu;
SELECT item_key,item_value,label,is_active,deleted FROM sys_dict_item
WHERE dict_code = 'product_file_category' ORDER BY sort_order,item_id;
