-- dev-20260901-1225 网版管理菜单（挂工程管理 90 下）
-- 幂等：NOT EXISTS 判断
INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '网版管理', menu_id, 61, 'screen', 'views/engineering/screen/index.vue', '1', '0', 'C', '0', '0',
       'engineering:screen:view', 'Grid', CONCAT('0,', menu_id), 'ScreenMaster', '1', 61, 'admin', NOW(), 'admin',
       '网版主数据管理（dev-20260901-1225）'
FROM sys_menu WHERE menu_id = 90
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'ScreenMaster');

-- 角色授权继承：沿用 BOM 管理(9) 的授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, target.menu_id
FROM sys_role_menu rm
JOIN sys_menu source ON source.menu_id = rm.menu_id AND source.route_name = 'Bom'
JOIN sys_menu target ON target.route_name = 'ScreenMaster';
