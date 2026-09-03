-- 43_purchase_report_menu.sql
-- 孤儿报表页处理（任务 1270 / dev-20260901-085）
-- 1) 采购报表挂菜单：采购管理(36)下新增 C 菜单 → views/purchase/report/index.vue（真实统计页）
-- 2) 删除孤儿 F 按钮 127 库存报表（inventory:report:view，挂在库存列表 26 下，页面为 mock 已删）
-- 幂等：动态匹配，不硬编码 menu_id。

-- 1. 新增 C 菜单 采购报表
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, route_name, ancestors, create_by, create_time, remark)
SELECT '采购报表', 36, 6, 'report', 'views/purchase/report/index.vue', 1, 0, 'C', '0', '0', 'purchase:report:view', 'TrendCharts', 'PurchaseReport', '0,36', 'admin', NOW(), '孤儿页挂菜单（1270）：真实统计页'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'PurchaseReport');

-- 2. 授权：复制兄弟菜单 采购订单(PurchaseOrder)的角色授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role_menu r
JOIN sys_menu src ON src.route_name = 'PurchaseOrder'
JOIN sys_menu m ON m.route_name = 'PurchaseReport'
WHERE r.menu_id = src.menu_id;

-- 3. 删除孤儿 F 按钮 127 库存报表（先删授权，再删菜单行）
DELETE r FROM sys_role_menu r
JOIN sys_menu m ON r.menu_id = m.menu_id
WHERE m.perms = 'inventory:report:view' AND m.parent_id = 26 AND m.menu_type = 'F';

DELETE m FROM sys_menu m
WHERE m.perms = 'inventory:report:view' AND m.parent_id = 26 AND m.menu_type = 'F';
