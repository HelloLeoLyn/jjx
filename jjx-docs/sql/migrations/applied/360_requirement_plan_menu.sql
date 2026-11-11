-- 360 需求计划菜单（dev-20261111-007）
-- risk: low（只增菜单与角色授权，不改动已有菜单）
-- 回滚：DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.path='requirement-plan' AND m.parent_id=43;
--       DELETE FROM sys_menu WHERE path='requirement-plan' AND parent_id=43;
-- 说明：页面路由已在 router 中存在（/production/requirement-plan），本迁移只补侧边栏入口与授权。
SET @parent := (SELECT menu_id FROM sys_menu WHERE menu_name='生产管理' AND parent_id=0 LIMIT 1);

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '需求计划',@parent,4,'requirement-plan','views/production/requirement-plan/index.vue','C','production:order:view','DataLine',0,0,'mig-360',NOW(),'mig-360',NOW(),'dev-20261111-007 多层净需求只读试算页'
 WHERE @parent IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@parent AND path='requirement-plan');

SET @menu := (SELECT menu_id FROM sys_menu WHERE parent_id=@parent AND path='requirement-plan' LIMIT 1);

-- 授权沿用：给「已有生产订单查看」的角色（超级管理员/PRODUCTION 全权限/PRODUCTION 业务操作）
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id,@menu
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.menu_id=rm.menu_id
 WHERE m.perms='production:order:view'
   AND @menu IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=@menu);
