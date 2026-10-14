-- 280_sales_delivery_doc_permissions.sql
-- dev-20261014-023（2026-10-14）：销售送货回签件接入公共附件（来源 sales_delivery）
--   新增独立权限域 sales:delivery:doc:view / sales:delivery:doc:add（挂在 发货管理菜单 218 下，照收货票据 393/394、采购发票 279 同款）
--   写权限用 :add（凭证式）。
-- 幂等：按 perms 判在，重复执行 0 影响。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms IN ('sales:delivery:doc:view','sales:delivery:doc:add'));
--   DELETE FROM sys_menu WHERE perms IN ('sales:delivery:doc:view','sales:delivery:doc:add');

-- ① 权限按钮（菜单插入必须显式给 icon 或 NULL）
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '回签件查看',218,6,NULL,NULL,'1','0','F','0','0','sales:delivery:doc:view',NULL,'0,13,218','1',0,'admin',NOW(),'销售发货-回签件查看'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:delivery:doc:view');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '回签件上传',218,7,NULL,NULL,'1','0','F','0','0','sales:delivery:doc:add',NULL,'0,13,218','1',0,'admin',NOW(),'销售发货-回签件上传'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:delivery:doc:add');

-- 授权：持有 发货管理菜单(218) 的角色 + 超级管理员(1)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id
      UNION SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id=218) r
WHERE m.perms IN ('sales:delivery:doc:view','sales:delivery:doc:add');
