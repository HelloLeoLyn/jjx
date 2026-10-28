-- dev-20261028-010：库存管理下新增二级目录「物料管理」+ 四个子菜单
-- risk: low（只增菜单与授权；不删不改已有菜单除 240/22 的 parent_id 挂载）
-- 回滚：见文件末尾注释（恢复 240/22 parent_id=18，删除新增菜单与授权）
SET @root := (SELECT menu_id FROM sys_menu WHERE menu_name='库存管理' AND parent_id=0 LIMIT 1);

-- 1) 二级目录：物料管理
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '物料管理',@root,0,'material-admin','layout/index.vue','M','inventory:material:view','Box',0,0,'mig-317',NOW(),'mig-317',NOW(),'dev-20261028-010 物料管理配置目录'
 WHERE @root IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name='物料管理' AND parent_id=@root);

SET @admin := (SELECT menu_id FROM sys_menu WHERE menu_name='物料管理' AND parent_id=@root LIMIT 1);

-- 2) 物料档案：复用现有 240（menu_id 不变），只改挂载
UPDATE sys_menu SET parent_id=@admin WHERE menu_id=240 AND @admin IS NOT NULL;
-- 物料详情 22 一并挂到新目录（menu_id 不变）
UPDATE sys_menu SET parent_id=@admin WHERE menu_id=22 AND @admin IS NOT NULL;

-- 3) 其余三个子菜单（按 path 幂等）
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '物料分类',@admin,2,'category','views/inventory/material/category.vue','C','inventory:category:list','Menu',0,0,'mig-317',NOW(),'mig-317',NOW(),'dev-20261028-010'
 WHERE @admin IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@admin AND path='category');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '属性模板',@admin,3,'attribute-template','views/inventory/material/attribute-template.vue','C','inventory:category:edit','SetUp',0,0,'mig-317',NOW(),'mig-317',NOW(),'dev-20261028-010'
 WHERE @admin IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@admin AND path='attribute-template');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '命名规则',@admin,4,'naming-rule','views/inventory/material/naming-rule.vue','C','inventory:category:edit','Edit',0,0,'mig-317',NOW(),'mig-317',NOW(),'dev-20261028-010'
 WHERE @admin IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@admin AND path='naming-rule');

-- 4) 角色授权沿用：把「有 240 物料列表权限」的角色复制到新菜单（幂等）
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.parent_id=@admin AND m.menu_id<>240 AND m.menu_id<>22
 WHERE rm.menu_id=240
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=m.menu_id);

-- 回滚（人工确认后执行）：
-- UPDATE sys_menu SET parent_id=18 WHERE menu_id IN (240,22);
-- DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.parent_id=@admin;
-- DELETE FROM sys_menu WHERE parent_id=@admin AND menu_id NOT IN (240,22);
-- DELETE FROM sys_menu WHERE menu_name='物料管理' AND parent_id=18;
