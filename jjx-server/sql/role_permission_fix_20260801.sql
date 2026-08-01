-- ============================================================
-- 角色权限补全（2026-08-01）
-- 工程/仓管/审核员 三个角色权限修复
-- 已在生产库执行，此文件用于追溯/其他环境同步
-- ============================================================

-- 1. 工程管理(9)：补样品单全套权限 + 父级销售管理
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 9, m.menu_id FROM sys_menu m
WHERE m.menu_id IN (13, 229, 230, 231, 232, 233, 234, 235, 236, 237)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=9 AND rm.menu_id=m.menu_id);

-- 2. 仓管(11)：补库存全权限（父级 + 所有 inventory 菜单）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 11, m.menu_id FROM sys_menu m
WHERE (m.menu_id IN (18,19) OR m.perms LIKE 'inventory%')
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=11 AND rm.menu_id=m.menu_id);

-- 3. 订单审核员(8)：补报价审核/样品单审核/采购审核 + 父级
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 8, m.menu_id FROM sys_menu m
WHERE m.menu_id IN (15, 88, 229, 233, 36, 38, 163)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=8 AND rm.menu_id=m.menu_id);
