-- 任务码：dev-20261107-014
-- 目的：物料品牌字典补「首字母大写缩写」——写入 sys_dict_item.ext_data.abbr，
--       供命名规则「品牌·缩写」(brandabbr) 勾选项取值。
-- 口径（用户 2026-11-07 定稿 1A）：只动 ext_data，不改 item_key / item_value / label，
--       存量 inventory_material.brand 引用（98 条物料）无需迁移。
-- 幂等：按 dict_code + item_value + tenant_id 定位，JSON_SET 写入同一值；重复执行影响行数不变。
-- 影响：字典配置数据（非表结构），按 CONVENTIONS §2「字典类配置」免备份；
--       手工全库备份 2026-11-07 已存在（jjx-docs/sql/backups/jjx_erp_db_backup_20261107-1028.sql）。

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', '3M')
WHERE dict_code = 'brand' AND item_value = '3M' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'TESA')
WHERE dict_code = 'brand' AND item_value = 'TESA' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'LJS')
WHERE dict_code = 'brand' AND item_value = 'ROGERS' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'SS')
WHERE dict_code = 'brand' AND item_value = 'SHANGSHENG' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'DB')
WHERE dict_code = 'brand' AND item_value = 'DIBO' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'LYY')
WHERE dict_code = 'brand' AND item_value = 'LONGYUEYU' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'GC')
WHERE dict_code = 'brand' AND item_value = 'GUANCHANG' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'JML')
WHERE dict_code = 'brand' AND item_value = 'JINMALAI' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'HA')
WHERE dict_code = 'brand' AND item_value = 'HUAAN' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'HY')
WHERE dict_code = 'brand' AND item_value = 'HONGYU' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'HF')
WHERE dict_code = 'brand' AND item_value = 'HONGFA' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'BM')
WHERE dict_code = 'brand' AND item_value = 'BOMING' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'CHL')
WHERE dict_code = 'brand' AND item_value = 'CHL' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'SH')
WHERE dict_code = 'brand' AND item_value = '三和' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'CXG')
WHERE dict_code = 'brand' AND item_value = 'CHENGXINGGUANG' AND tenant_id = 1;

UPDATE sys_dict_item SET ext_data = JSON_SET(COALESCE(ext_data, '{}'), '$.abbr', 'CYG')
WHERE dict_code = 'brand' AND item_value = 'CHENGYIGUANG' AND tenant_id = 1;

-- 回查：16 条品牌应全部带 abbr
SELECT item_value, label, JSON_UNQUOTE(JSON_EXTRACT(ext_data, '$.abbr')) AS abbr
FROM sys_dict_item WHERE dict_code = 'brand' AND tenant_id = 1 ORDER BY sort_order, item_id;
