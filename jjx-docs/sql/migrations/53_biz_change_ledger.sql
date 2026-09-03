-- 53_biz_change_ledger.sql
-- 变更记录台账（任务 1302 / dev-20260902-101）
-- 业务管理(317)下新增 C 菜单 变更记录 → views/biz/requirement/changes.vue（QR-071 电子台账）
-- 幂等：动态匹配，不硬编码 menu_id。

-- 1. 新增 C 菜单 变更记录
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, route_name, ancestors, create_by, create_time, remark)
SELECT '变更记录', 317, 2, 'changes', 'views/biz/requirement/changes.vue', 1, 0, 'C', '0', '0', 'biz:requirement:view', 'Document', 'BizChanges', '0,317', 'admin', NOW(), '变更记录台账 QR-071（1302）：biz_requirement CHANGE 清单+导出'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'BizChanges');

-- 2. 授权：复制兄弟菜单 需求管理(route_name='Requirement')的角色授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role_menu r
JOIN sys_menu src ON src.route_name = 'Requirement'
JOIN sys_menu m ON m.route_name = 'BizChanges'
WHERE r.menu_id = src.menu_id;
