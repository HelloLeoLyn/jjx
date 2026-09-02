-- dev-20260901-1235 销售退货管理菜单。
-- 新增 C 菜单（退货管理）+ F 按钮（新增/审核/编辑），角色授权沿用销售订单。
-- 幂等：NOT EXISTS 判断。

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '退货管理', menu_id, 11, 'return', 'views/sales/return/index.vue', '1', '0', 'C', '0', '0',
       'sales:return:view', 'RefreshLeft', CONCAT('0,', menu_id), 'SalesReturn', '1', 11, 'admin', NOW(), 'admin',
       '销售退货单管理（dev-20260901-1235）'
FROM sys_menu WHERE path = '/sales' AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SalesReturn');

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '新增退货', menu_id, 1, '', NULL, '1', '0', 'F', '0', '0',
       'sales:return:add', '', CONCAT('0,13,', menu_id), NULL, '1', 1, 'admin', NOW(), 'admin', ''
FROM sys_menu WHERE route_name = 'SalesReturn'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'sales:return:add');

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '审核退货', menu_id, 2, '', NULL, '1', '0', 'F', '0', '0',
       'sales:return:approve', '', CONCAT('0,13,', menu_id), NULL, '1', 2, 'admin', NOW(), 'admin', ''
FROM sys_menu WHERE route_name = 'SalesReturn'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'sales:return:approve');

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '编辑退货', menu_id, 3, '', NULL, '1', '0', 'F', '0', '0',
       'sales:return:edit', '', CONCAT('0,13,', menu_id), NULL, '1', 3, 'admin', NOW(), 'admin', ''
FROM sys_menu WHERE route_name = 'SalesReturn'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'sales:return:edit');

-- 沿用销售订单菜单的角色授权，避免仅管理员可见。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, target.menu_id
FROM sys_role_menu rm
JOIN sys_menu source ON source.menu_id = rm.menu_id AND source.route_name = 'SalesOrder'
JOIN sys_menu target ON target.route_name = 'SalesReturn';
