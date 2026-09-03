-- 42_sales_dashboard_permission.sql
-- 销售成员工作台权限点（任务1275）
-- 背景：dashboard widget 权限 sales:dashboard 从未注册，v-hasPermi 恒 false（仅 admin 通配可见），
--      新接口 /dashboard/sales-workbench 需要该权限点鉴权。
-- 挂销售管理(13)目录下 F 按钮：目录无页面组件，F 不渲染菜单项，不污染 UI。
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '销售工作台', 13, 99, '', NULL, 1, 1, 'F', '0', '0', 'sales:dashboard', '#', 'admin', NOW(), '首页销售工作台widget（1275）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'sales:dashboard');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r JOIN sys_menu m ON m.perms = 'sales:dashboard'
WHERE r.role_key IN ('sales:all', 'sales:ops');
