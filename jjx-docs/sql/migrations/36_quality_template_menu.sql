-- dev-20260901 质量记录模板维护菜单入口（挂生产管理 43 下）
-- 幂等：NOT EXISTS 判断；route_name 用新名避免与静态 hidden 路由冲突
INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '质量记录模板', menu_id, 7, 'quality-template', 'views/production/quality-template/index.vue', '1', '0', 'C', '0', '0',
       'production:quality-template:view', 'Document', CONCAT('0,', menu_id), 'ProductionQualityTemplateMenu', '1', 7, 'admin', NOW(), 'admin',
       '质量记录一览表维护（dev-20260901）'
FROM sys_menu WHERE menu_id = 43
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:quality-template:view');

-- 角色授权继承：沿用质检管理(264) 的授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, target.menu_id
FROM sys_role_menu rm
JOIN sys_menu source ON source.menu_id = rm.menu_id AND source.route_name = 'ProductionQuality'
JOIN sys_menu target ON target.route_name = 'ProductionQualityTemplateMenu';
