-- dev-20261105-012：物料管理新增「物料字典」，仅维护 brand/color/form/unit
-- risk: high（新增 sys_menu / sys_role_menu；本卡只提交，待用户手工备份后经 db-migrate.sh 执行）
-- 幂等：菜单按 parent_id + path 判重，角色授权按 role_id + menu_id 判重。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '物料字典',428,5,'dictionary','views/inventory/material/dictionary.vue','C','inventory:material:dict:view','Collection',0,0,'mig-342',NOW(),'mig-342',NOW(),'dev-20261105-012 物料字典维护'
 WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=428)
   AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=428 AND path='dictionary');

SET @dictionary := (SELECT menu_id FROM sys_menu WHERE parent_id=428 AND path='dictionary' LIMIT 1);

-- 独立编辑权限按钮：只授给本次新入口，不向 INVENTORY 开放 system:dict:*。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,menu_type,perms,icon,status,visible,create_by,create_time,update_by,update_time,remark)
SELECT '维护物料字典',@dictionary,1,'',NULL,'F','inventory:material:dict:edit',NULL,0,0,'mig-342',NOW(),'mig-342',NOW(),'dev-20261105-012 新增/修改/启停/删除物料字典项'
 WHERE @dictionary IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=@dictionary AND perms='inventory:material:dict:edit');

-- 入口和编辑按钮均复制 429「物料分类」的授权，不硬编码 role_id；不改已有菜单/授权。
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id,m.menu_id
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.menu_id=@dictionary
    OR (m.parent_id=@dictionary AND m.perms='inventory:material:dict:edit')
 WHERE rm.menu_id=429
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=m.menu_id);
