-- 51_sales_reconciliation_menu.sql
-- 业务对账单（任务1299，2026-09-03）
-- 纯查询+页面：按客户+期间列送货明细（delivery 1:1 订单明细，模型天然精确）+ 期间回款合计
-- 无 QR 编号（registry 无业务对帐单），打印不走留痕

-- 1. 新增 C 菜单 业务对账（销售管理 13 下）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, route_name, ancestors, create_by, create_time, remark)
SELECT '业务对账', 13, 12, 'reconcile', 'views/sales/reconcile/index.vue', 1, 0, 'C', '0', '0', 'sales:reconcile:view', 'Document', 'SalesReconcile', '0,13', 'admin', NOW(), '业务对账单（1299）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SalesReconcile');

-- 2. 授权：复制兄弟菜单 销售订单(SalesOrder)的角色授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role_menu r
JOIN sys_menu src ON src.route_name = 'SalesOrder'
JOIN sys_menu m ON m.route_name = 'SalesReconcile'
WHERE r.menu_id = src.menu_id;
