-- 45_production_report_menu.sql
-- 孤儿页挂菜单（任务 1313 / dev-20260903-107）
-- 核实：views/production/report/index.vue = 真实报表页（产量/效率/质量 3 tabs，
--       调真实接口 /production/report/output|efficiency|quality，ProductionReportController 存在），
--       按 1270 同款处理（采购报表真实统计页→挂菜单），挂到生产管理(43)
-- print.vue 为"生产报工单（工票）打印页"（getWorkReport 真实接口），非本报表页的打印，归任务 1247 接入口，不删
-- 幂等：route_name 匹配，不硬编码 menu_id。

-- 1. 新增 C 菜单 生产报表
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, route_name, ancestors, create_by, create_time, remark)
SELECT '生产报表', 43, 7, 'report', 'views/production/report/index.vue', 1, 0, 'C', '0', '0', 'production:report:view', 'TrendCharts', 'ProductionReport', '0,43', 'admin', NOW(), '孤儿页挂菜单（1313）：真实统计页'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'ProductionReport');

-- 2. 授权：复制兄弟菜单 生产订单(ProductionOrder)的角色授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role_menu r
JOIN sys_menu src ON src.route_name = 'ProductionOrder'
JOIN sys_menu m ON m.route_name = 'ProductionReport'
WHERE r.menu_id = src.menu_id;
