-- 263_work_spec_publish_permission.sql
-- dev-20261011-008（2026-10-11）：发布作业规范版本 权限按钮 + 授权
-- 幂等：按 perms 判在，重复执行 0 影响。
-- 回滚：DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms='product:work-spec:publish');
--       DELETE FROM sys_menu WHERE perms='product:work-spec:publish';
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '发布作业规范版本',407,20,NULL,NULL,'1','0','F','0','0','product:work-spec:publish',NULL,'0,6,407','1',0,'admin',NOW(),'产品/工程-发布作业规范版本'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='product:work-spec:publish');

-- 授权：持有 产品作业规范(407: 12/14/15) 与 生产作业规范(404: 16) 的角色 + 超级管理员(1)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id UNION ALL SELECT 12 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17) r
WHERE m.perms = 'product:work-spec:publish';
