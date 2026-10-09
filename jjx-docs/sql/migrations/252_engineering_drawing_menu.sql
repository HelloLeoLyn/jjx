-- risk: low
-- dev-20261009-023：工程管理下新增「图纸管理」菜单（工程图/技术文档受控与下发，同源产品文件库）。
-- 幂等：已存在则跳过。
START TRANSACTION;
SET @eng := (SELECT menu_id FROM sys_menu WHERE perms='engineering:view' AND parent_id=0 LIMIT 1);
SET @anc := (SELECT CONCAT(COALESCE(ancestors,'0'),',',menu_id) FROM sys_menu WHERE menu_id=@eng);
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,remark)
SELECT '图纸管理',@eng,5,'drawing','views/engineering/drawing/index.vue',1,0,'C','0','0',
       'engineering:drawing:view','Picture',@anc,1,'dahuang',NOW(),
       'dev-20261009-023：图纸/工程文件受控与下发（同源产品文件库，不改产品作业规范）'
WHERE @eng IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:drawing:view');

SET @dm := (SELECT menu_id FROM sys_menu WHERE perms='engineering:drawing:view' LIMIT 1);
SET @anc2 := (SELECT CONCAT(COALESCE(ancestors,'0'),',',menu_id) FROM sys_menu WHERE menu_id=@dm);
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,remark)
SELECT '下发图纸',@dm,1,'',NULL,1,0,'F','0','0',
       'engineering:drawing:release',NULL,@anc2,1,'dahuang',NOW(),'dev-20261009-023：图纸下发/撤回'
WHERE @dm IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:drawing:release');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18) r
JOIN sys_menu m ON m.perms IN ('engineering:drawing:view','engineering:drawing:release')
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
COMMIT;
SELECT menu_id,parent_id,menu_name,path,perms FROM sys_menu WHERE perms LIKE 'engineering:drawing:%';
