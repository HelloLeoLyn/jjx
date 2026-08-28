-- dev-20260827-032 收款单/销售发票管理菜单。
-- 两项暂挂销售管理；未来迁移至独立财务模块时，仅需调整 parent_id/ancestors，页面路由保持稳定。
INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '收款单管理', menu_id, 9, 'receipt', 'views/sales/receipt/index.vue', '1', '0', 'C', '0', '0',
       'sales:order:view', 'Money', CONCAT('0,', menu_id), 'SalesReceipt', '1', 9, 'admin', NOW(), 'admin',
       '可迁移销售财务单据菜单（dev-20260827-032）'
FROM sys_menu WHERE path = '/sales' AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SalesReceipt');

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '销售发票', menu_id, 10, 'invoice', 'views/sales/invoice/index.vue', '1', '0', 'C', '0', '0',
       'sales:order:view', 'Tickets', CONCAT('0,', menu_id), 'SalesInvoice', '1', 10, 'admin', NOW(), 'admin',
       '可迁移销售财务单据菜单（dev-20260827-032）'
FROM sys_menu WHERE path = '/sales' AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'SalesInvoice');

-- 沿用销售订单菜单的角色授权，避免迁移后仅管理员可见。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, target.menu_id
FROM sys_role_menu rm
JOIN sys_menu source ON source.menu_id = rm.menu_id AND source.route_name = 'SalesOrder'
JOIN sys_menu target ON target.route_name IN ('SalesReceipt', 'SalesInvoice');
