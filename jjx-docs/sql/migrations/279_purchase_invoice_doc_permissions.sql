-- 279_purchase_invoice_doc_permissions.sql
-- dev-20261014-022（2026-10-14）：采购发票文件接入公共附件（来源 purchase_invoice）
--   新增独立权限域 purchase:invoice:doc:view / purchase:invoice:doc:add（挂在采购发票菜单 165 下，照收货票据 393/394 同款）
-- 幂等：按 perms 判在，重复执行 0 影响。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms IN ('purchase:invoice:doc:view','purchase:invoice:doc:add'));
--   DELETE FROM sys_menu WHERE perms IN ('purchase:invoice:doc:view','purchase:invoice:doc:add');

-- ① 权限按钮（菜单插入必须显式给 icon 或 NULL）
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '发票文件查看',165,6,NULL,NULL,'1','0','F','0','0','purchase:invoice:doc:view',NULL,'0,36,165','1',0,'admin',NOW(),'采购发票-发票文件查看'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:invoice:doc:view');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '发票文件上传',165,7,NULL,NULL,'1','0','F','0','0','purchase:invoice:doc:add',NULL,'0,36,165','1',0,'admin',NOW(),'采购发票-发票文件上传'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='purchase:invoice:doc:add');

-- 授权：持有 采购发票菜单(165) 的角色 + 超级管理员(1)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id
      UNION SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id=165) r
WHERE m.perms IN ('purchase:invoice:doc:view','purchase:invoice:doc:add');
