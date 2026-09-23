-- dev-20260923-022：不良处置「撤销」权限点（撤销流，dev-20260923-022 二期）
--
-- 背景：022 实现「失效批禁止处置 + 随批作废」，但已生效(DONE)的处置结论此前没有更正入口。
--       按业内口径（SAP QM 使用决策 UD 可 reset 但必须带权限+原因+审计），撤销是**受控动作**：
--       单独权限点 + 必填原因 + remark 留痕（本期先支持 SCRAP：无库存影响，安全可逆）。
--
-- 授权口径（与现有 quality:ncr:dispose 的角色对齐）：roles 1 超级管理员 / 28 PRODUCTION 全权限 / 34 QUALITY 品质主管
-- 幂等：菜单按 perms 判重插入；授权 INSERT IGNORE。

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, update_by)
SELECT '不良处置撤销', 378, 2, '', NULL, 'F', 0, 0, 'quality:ncr:revoke', NULL, '0,338,378', 1, 'admin', 'admin'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'quality:ncr:revoke');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  JOIN sys_menu m ON m.perms = 'quality:ncr:revoke'
 WHERE r.role_id IN (1, 28, 34);
