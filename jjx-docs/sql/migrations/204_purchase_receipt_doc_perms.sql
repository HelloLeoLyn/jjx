-- dev-20260923-004：采购收货 —— 收货票据独立权限点（方案 A2 配套）
--
-- 背景：收货弹窗支持上传票据，但原实现走「采购发票」域端点（purchase:invoice:*），
--       收货员（PURCHASE 业务操作等）若无发票权限则无法上传/登记票据。
--       本次把票据收口到收货域：后端 /purchase/receipt/doc/* 用 purchase:receipt:doc:*，
--       本迁移只补「权限点 + 角色授权」，不改表结构、不动数据。
--
-- 授权口径（按已有收货权限对齐，见 sys_role_menu 现状）：
--   view  → 已有 purchase:receipt:view 的角色：1 超级管理员 / 25 PURCHASE全权限 / 26 PURCHASE业务操作
--                                                  / 27 PURCHASE审核员 / 33 QUALITY来料检验员
--   add   → 已有 purchase:receipt:add 的角色：1 / 25 / 26
--   delete→ 已有 purchase:receipt:delete 的角色：1 / 25 / 26
--
-- 幂等：菜单按 perms 判重插入；授权用 INSERT IGNORE（role_id+menu_id 主键）。

-- 1) 权限点（F 型按钮，挂 采购收货(180) 下；icon 显式 NULL，避免 sys_menu.icon 脏值导致前端图标崩溃）
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '收货票据查看', 180, 6, '', NULL, 'F', 0, 0, 'purchase:receipt:doc:view', NULL, '0,36,180', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'purchase:receipt:doc:view');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '收货票据上传', 180, 7, '', NULL, 'F', 0, 0, 'purchase:receipt:doc:add', NULL, '0,36,180', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'purchase:receipt:doc:add');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '收货票据删除', 180, 8, '', NULL, 'F', 0, 0, 'purchase:receipt:doc:delete', NULL, '0,36,180', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'purchase:receipt:doc:delete');

-- 2) 角色授权（view：角色 1/25/26/27/33）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'purchase:receipt:doc:view'
 WHERE r.role_id IN (1, 25, 26, 27, 33);

-- 3) 角色授权（add：角色 1/25/26）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'purchase:receipt:doc:add'
 WHERE r.role_id IN (1, 25, 26);

-- 4) 角色授权（delete：角色 1/25/26）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'purchase:receipt:doc:delete'
 WHERE r.role_id IN (1, 25, 26);
