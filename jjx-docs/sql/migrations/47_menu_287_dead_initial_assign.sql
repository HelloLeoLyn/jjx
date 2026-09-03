-- 43_menu_287_dead_initial_assign.sql
-- 清理死按钮权限：menu 287「初始分配」(production:task:dispatch)（任务1267）
-- 证据：production:task:dispatch 前端/后端零代码引用；261 派工管理页早已改造为新模型任务树（ProductionTask），
--       实际按钮权限用 production:task:view/assign/return/recall，无 dispatch。
-- 保留一行历史备份注释；先删角色关联再删菜单。
DELETE FROM sys_role_menu WHERE menu_id = 287;
DELETE FROM sys_menu WHERE menu_id = 287 AND perms = 'production:task:dispatch';
