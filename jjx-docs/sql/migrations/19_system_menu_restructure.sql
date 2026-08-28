-- dev-20260828-039 系统管理菜单重构。
-- 本迁移可重复执行；仅调整系统管理子树、旧日志目录及汇率兜底配置。

-- 新增三级目录。
INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '组织权限', 1, 1, 'org', '', '1', '0', 'M', '0', '0',
       'system:view', 'UserFilled', '0,1', 'SystemOrg', '1', 1, 'admin', NOW(), 'admin',
       '系统管理组织权限目录（dev-20260828-039）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SystemOrg');

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '基础配置', 1, 2, 'setting', '', '1', '0', 'M', '0', '0',
       'system:view', 'Setting', '0,1', 'SystemSetting', '1', 2, 'admin', NOW(), 'admin',
       '系统管理基础配置目录（dev-20260828-039）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SystemSetting');

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '运维监控', 1, 3, 'ops', '', '1', '0', 'M', '0', '0',
       'system:view', 'Monitor', '0,1', 'SystemOps', '1', 3, 'admin', NOW(), 'admin',
       '系统管理运维监控目录（dev-20260828-039）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SystemOps');

-- 若曾部分执行，仍将目录收敛到规格定义。
UPDATE sys_menu SET menu_name = '组织权限', parent_id = 1, order_num = 1, path = 'org', component = '',
  menu_type = 'M', visible = '0', status = '0', icon = 'UserFilled', ancestors = '0,1', sort = 1
WHERE route_name = 'SystemOrg';
UPDATE sys_menu SET menu_name = '基础配置', parent_id = 1, order_num = 2, path = 'setting', component = '',
  menu_type = 'M', visible = '0', status = '0', icon = 'Setting', ancestors = '0,1', sort = 2
WHERE route_name = 'SystemSetting';
UPDATE sys_menu SET menu_name = '运维监控', parent_id = 1, order_num = 3, path = 'ops', component = '',
  menu_type = 'M', visible = '0', status = '0', icon = 'Monitor', ancestors = '0,1', sort = 3
WHERE route_name = 'SystemOps';

-- 新增只读汇率查看菜单。
INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '汇率管理', setting_menu.menu_id, 4, 'exchange-rate', 'views/system/exchange-rate/index.vue',
       '1', '0', 'C', '0', '0', 'system:config:view', 'Money',
       CONCAT('0,1,', setting_menu.menu_id), 'SystemExchangeRate', '1', 4,
       'admin', NOW(), 'admin', '汇率查看页（dev-20260828-039）'
FROM sys_menu setting_menu
WHERE setting_menu.route_name = 'SystemSetting'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SystemExchangeRate');

UPDATE sys_menu child
JOIN sys_menu parent ON parent.route_name = 'SystemSetting'
SET child.menu_name = '汇率管理', child.parent_id = parent.menu_id, child.order_num = 4,
    child.path = 'exchange-rate', child.component = 'views/system/exchange-rate/index.vue',
    child.menu_type = 'C', child.visible = '0', child.status = '0', child.icon = 'Money',
    child.ancestors = CONCAT('0,1,', parent.menu_id), child.sort = 4
WHERE child.route_name = 'SystemExchangeRate';

-- 沿用系统参数菜单的角色授权。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, exchange_menu.menu_id
FROM sys_role_menu rm
JOIN sys_menu config_menu ON config_menu.menu_id = rm.menu_id AND config_menu.menu_id = 250
JOIN sys_menu exchange_menu ON exchange_menu.route_name = 'SystemExchangeRate';

-- 移动组织权限菜单。
UPDATE sys_menu child
JOIN sys_menu parent ON parent.route_name = 'SystemOrg'
SET child.parent_id = parent.menu_id,
    child.ancestors = CONCAT('0,1,', parent.menu_id),
    child.order_num = CASE child.menu_id WHEN 2 THEN 1 WHEN 3 THEN 2 WHEN 5 THEN 3 WHEN 4 THEN 4 END,
    child.sort = CASE child.menu_id WHEN 2 THEN 1 WHEN 3 THEN 2 WHEN 5 THEN 3 WHEN 4 THEN 4 END
WHERE child.menu_id IN (2, 3, 4, 5);

-- 移动基础配置菜单并统一名称、图标。
UPDATE sys_menu child
JOIN sys_menu parent ON parent.route_name = 'SystemSetting'
SET child.parent_id = parent.menu_id,
    child.ancestors = CONCAT('0,1,', parent.menu_id),
    child.order_num = CASE child.menu_id WHEN 61 THEN 1 WHEN 250 THEN 2 WHEN 238 THEN 3 END,
    child.sort = CASE child.menu_id WHEN 61 THEN 1 WHEN 250 THEN 2 WHEN 238 THEN 3 END,
    child.menu_name = CASE child.menu_id WHEN 250 THEN '系统参数' ELSE child.menu_name END,
    child.icon = CASE child.menu_id WHEN 61 THEN 'Collection' WHEN 250 THEN 'Setting' WHEN 238 THEN 'Bell' END
WHERE child.menu_id IN (61, 238, 250);

-- 移动运维监控菜单及文件管理，明确排序和图标。
UPDATE sys_menu child
JOIN sys_menu parent ON parent.route_name = 'SystemOps'
SET child.parent_id = parent.menu_id,
    child.ancestors = CONCAT('0,1,', parent.menu_id),
    child.order_num = CASE child.menu_id WHEN 56 THEN 1 WHEN 57 THEN 2 WHEN 58 THEN 3 WHEN 251 THEN 4 END,
    child.sort = CASE child.menu_id WHEN 56 THEN 1 WHEN 57 THEN 2 WHEN 58 THEN 3 WHEN 251 THEN 4 END,
    child.icon = CASE child.menu_id WHEN 56 THEN 'Tickets' WHEN 57 THEN 'Key' WHEN 58 THEN 'Warning' WHEN 251 THEN 'Folder' END
WHERE child.menu_id IN (56, 57, 58, 251);

-- 三个新目录继承其任一直接子菜单的角色授权，避免 RouterHelper 丢弃整片子树。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, parent.menu_id
FROM sys_role_menu rm
JOIN sys_menu child ON child.menu_id = rm.menu_id
JOIN sys_menu parent ON parent.menu_id = child.parent_id
WHERE parent.route_name IN ('SystemOrg', 'SystemSetting', 'SystemOps');

-- 删除已被替代的菜单及其授权。
DELETE rm FROM sys_role_menu rm JOIN sys_menu menu ON menu.menu_id = rm.menu_id
WHERE menu.menu_id IN (55, 249);
DELETE FROM sys_menu WHERE menu_id IN (55, 249);

-- 系统管理置于业务模块之后，历史 sort 列保持一致。
UPDATE sys_menu SET order_num = 900, sort = 900 WHERE menu_id = 1;

-- 汇率兜底初始值；现有管理员修改不会被重复迁移覆盖。
INSERT IGNORE INTO sys_config
  (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
VALUES
  ('exchange_rate.CNY', '1.0000', '人民币', 'exchange_rate', '1 CNY 兑换人民币的兜底汇率', 1, 1),
  ('exchange_rate.USD', '7.2400', '美元', 'exchange_rate', '1 美元兑换人民币的兜底汇率', 2, 1),
  ('exchange_rate.EUR', '7.8800', '欧元', 'exchange_rate', '1 欧元兑换人民币的兜底汇率', 3, 1),
  ('exchange_rate.GBP', '9.3500', '英镑', 'exchange_rate', '1 英镑兑换人民币的兜底汇率', 4, 1),
  ('exchange_rate.JPY', '0.0480', '日元', 'exchange_rate', '1 日元兑换人民币的兜底汇率', 5, 1),
  ('exchange_rate.HKD', '0.9270', '港币', 'exchange_rate', '1 港币兑换人民币的兜底汇率', 6, 1),
  ('exchange_rate.KRW', '0.0053', '韩元', 'exchange_rate', '1 韩元兑换人民币的兜底汇率', 7, 1),
  ('exchange_rate.AUD', '4.7500', '澳元', 'exchange_rate', '1 澳元兑换人民币的兜底汇率', 8, 1),
  ('exchange_rate.CAD', '5.2700', '加拿大元', 'exchange_rate', '1 加拿大元兑换人民币的兜底汇率', 9, 1),
  ('exchange_rate.SGD', '5.3800', '新加坡元', 'exchange_rate', '1 新加坡元兑换人民币的兜底汇率', 10, 1),
  ('exchange_rate.TWD', '0.2230', '新台币', 'exchange_rate', '1 新台币兑换人民币的兜底汇率', 11, 1),
  ('exchange_rate.CHF', '8.1400', '瑞士法郎', 'exchange_rate', '1 瑞士法郎兑换人民币的兜底汇率', 12, 1);
