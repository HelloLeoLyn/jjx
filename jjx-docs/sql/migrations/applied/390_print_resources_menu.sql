-- 390_print_resources_menu.sql
-- 任务码：dev-20261125-013
-- 目的：新增「网版作业」独立页面菜单（/engineering/print-resources），挂到「工艺资源」(370) 下。
-- risk：低（仅新增一条菜单 + 授权按角色另配）
-- 回滚：DELETE FROM sys_menu WHERE component='views/engineering/print-resources/index.vue';
-- 幂等：按 component 判断存在性

SET NAMES utf8mb4;

SET @exists := (SELECT COUNT(*) FROM sys_menu WHERE component = 'views/engineering/print-resources/index.vue');
SET @ancestors := (SELECT CONCAT(ancestors, ',370') FROM sys_menu WHERE menu_id = 370);
SET @sql := IF(@exists = 0,
  CONCAT("INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,update_by,update_time,remark) VALUES ('网版作业',370,4,'print-resources','views/engineering/print-resources/index.vue',1,0,'C','0','0','engineering:screen-resource:view','Screen','", @ancestors, "',1,'dahuang',NOW(),'dahuang',NOW(),'dev-20261125-013：印刷资源网框与网版独立作业页')"),
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
