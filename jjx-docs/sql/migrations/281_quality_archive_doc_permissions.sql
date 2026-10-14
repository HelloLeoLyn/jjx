-- 281_quality_archive_doc_permissions.sql
-- dev-20261014-025（2026-10-14）：质量档案回传扫描件接入公共附件（来源 quality_archive）
--   新增独立权限域 production:quality-archive:doc:view / production:quality-archive:doc:add
--   （挂在 质量记录归档菜单 334 下，照收货票据 393/394、采购发票 279、送货回签件 280 同款）
--   写权限用 :add（凭证式）。
-- 幂等：按 perms 判在，重复执行 0 影响。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms IN ('production:quality-archive:doc:view','production:quality-archive:doc:add'));
--   DELETE FROM sys_menu WHERE perms IN ('production:quality-archive:doc:view','production:quality-archive:doc:add');

-- ① 权限按钮（菜单插入必须显式给 icon 或 NULL）
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '回传件查看',334,1,NULL,NULL,'1','0','F','0','0','production:quality-archive:doc:view',NULL,'0,316,334','1',0,'admin',NOW(),'质量档案-回传件查看'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='production:quality-archive:doc:view');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '回传件上传',334,2,NULL,NULL,'1','0','F','0','0','production:quality-archive:doc:add',NULL,'0,316,334','1',0,'admin',NOW(),'质量档案-回传件上传'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='production:quality-archive:doc:add');

-- 授权：持有 质量记录归档菜单(334) 的角色 + 超级管理员(1)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id
      UNION SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id=334) r
WHERE m.perms IN ('production:quality-archive:doc:view','production:quality-archive:doc:add');
