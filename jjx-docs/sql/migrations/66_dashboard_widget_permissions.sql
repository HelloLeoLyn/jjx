-- 首页角色工作台 widget 权限点（挂各自域目录下，授权随父复制，运营可勾减）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, visible, perms)
SELECT 43, '生产概况(首页工作台)', 'F', 0, 'production:dashboard'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:dashboard');
SET @m1 = LAST_INSERT_ID();
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @m1 FROM sys_role_menu WHERE menu_id = 43
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE menu_id = @m1 AND role_id = sys_role_menu.role_id);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, visible, perms)
SELECT 1, '公司总览(首页工作台)', 'F', 0, 'admin:dashboard'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'admin:dashboard');
SET @m2 = LAST_INSERT_ID();
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @m2 FROM sys_role_menu WHERE menu_id = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE menu_id = @m2 AND role_id = sys_role_menu.role_id);
