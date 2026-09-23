-- dev-20260923-039（块1）：状态×动作唯一出处所需的三枚按钮权限点
--
-- 依据：jjx-docs/design/quality-disposition-rework-master-dev-20260923-045.md §4.2（权限点定稿）
--   ① quality:ncr:void-superseded   随批作废存量失效批的不良单（纠错动作，敏感度高于普通处置 → 只给品质主管）
--   ② inventory:outbound:rework-supplement  返工补料开单（产生出库单 → 权限归库存域；入口在质量台账，见方案 §4.2 唯一例外）
--   ③ inventory:outbound:rework-return      返工退料开单（同上；实现在 dev-20260923-043）
--
-- 授权口径：超级管理员(1) + QUALITY 品质主管(34)；补料/退料另加 INVENTORY 全权限(22) 便于库管代开。
-- 幂等：菜单按 perms 判重插入；授权 INSERT IGNORE。

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '随批作废', 378, 3, '', NULL, 'F', 0, 0, 'quality:ncr:void-superseded', NULL, '0,338,378', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'quality:ncr:void-superseded');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '返工补料', 33, 5, '', NULL, 'F', 0, 0, 'inventory:outbound:rework-supplement', NULL, '0,18,33', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inventory:outbound:rework-supplement');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '返工退料', 33, 6, '', NULL, 'F', 0, 0, 'inventory:outbound:rework-return', NULL, '0,18,33', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'inventory:outbound:rework-return');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'quality:ncr:void-superseded'
 WHERE r.role_id IN (1, 34);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'inventory:outbound:rework-supplement'
 WHERE r.role_id IN (1, 22, 34);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'inventory:outbound:rework-return'
 WHERE r.role_id IN (1, 22, 34);
