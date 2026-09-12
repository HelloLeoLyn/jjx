-- dev-20260912-014
-- 工艺资源改为目录，下面分别挂菲林、网版、刀模。
-- 执行记录：2026-09-12 22:30 Codex；备份 md5=c1458df87bd489d132a6e0976028316e；ops.schema.version=104。
UPDATE sys_menu SET menu_type='M',component=NULL,perms=NULL,route_name=NULL,update_by='Codex',update_time=NOW() WHERE menu_id=370;
UPDATE sys_menu SET parent_id=370,order_num=1,path='film',ancestors='0,90,370',update_by='Codex',update_time=NOW() WHERE menu_id=92;
UPDATE sys_menu SET ancestors='0,90,370',update_by='Codex',update_time=NOW() WHERE parent_id=370 AND menu_type='F' AND perms LIKE 'engineering:resource:%';
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,sort,create_by,create_time,update_by,remark)
SELECT '网版管理',370,2,'screen','views/engineering/resource/index.vue','C','0','0','engineering:screen-resource:view','Grid','0,90,370','EngineeringScreenResource','1',2,'Codex',NOW(),'Codex','网框、制版、洗版及维护履历' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:screen-resource:view');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,sort,create_by,create_time,update_by,remark)
SELECT '刀模管理',370,3,'die','views/engineering/resource/index.vue','C','0','0','engineering:die-resource:view','Tools','0,90,370','EngineeringDieResource','1',3,'Codex',NOW(),'Codex','刀模档案、产品关联及维护履历' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:die-resource:view');
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT s.role_id,t.menu_id FROM (SELECT role_id FROM sys_role_menu WHERE menu_id IN (90,370,92)) s JOIN sys_menu t ON t.menu_id=370 OR t.parent_id=370 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=s.role_id AND e.menu_id=t.menu_id);
INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by,create_time,description)
SELECT 'dev-20260912-014','DEV','dev','修正工艺资源三级菜单结构',0,'P1','Codex',NOW(),'工艺资源改为目录，独立挂载菲林、网版、刀模，取消标签式入口并拆分权限。' WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE task_code='dev-20260912-014');
