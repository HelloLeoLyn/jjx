-- risk: low
-- dev-20261010-021：工程图种新增「刀模参数图」。
-- 仅幂等新增 1 条字典项（product_file_category）；无 DDL、不改附件/路径/角色授权。
-- 前端常量 ENGINEERING_DRAWING_CATEGORIES 同步加「刀模参数图」（下单下拉是 字典∩常量）。
-- 由用户部署时执行；字典按需拉取、无服务端缓存，前端刷新即生效。
START TRANSACTION;

INSERT INTO sys_dict_item
    (dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'product_file_category','die_param_drawing','刀模参数图','刀模参数图',18,1,0,1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item d
    WHERE d.dict_code = 'product_file_category'
      AND (d.item_key = 'die_param_drawing' OR d.item_value = '刀模参数图')
);

-- 让「其他工程图」保持末位（仅字典展示排序；下拉顺序仍按前端常量）
UPDATE sys_dict_item SET sort_order = 19
WHERE dict_code = 'product_file_category' AND item_value = '其他工程图';

COMMIT;
SELECT item_key,item_value,label,sort_order,is_active FROM sys_dict_item
WHERE dict_code = 'product_file_category' ORDER BY sort_order,item_id;
