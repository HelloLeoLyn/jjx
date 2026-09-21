-- ============================================================================
-- 167: 隔离处置权收口（用户 2026-09-21 定口径 B）
-- 任务：dev-20260921-028（task_id 2087「待拍板：隔离处置权给谁」→ 定 B）
-- 口径：隔离处置只给品质主管一侧；INVENTORY 业务操作(23)/审核员(24) 收回入口。
-- 风险：中（权限变更；按 CONVENTIONS §2 由 db-migrate.sh 自动全库备份）
-- ============================================================================
USE `jjx_erp_db`;

-- ① 收回「不合格品处置」菜单(333) 对 角色23 INVENTORY 业务操作 / 角色24 INVENTORY 审核员 的授权
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id IN (23, 24)
  AND m.menu_id = 333;

-- 核验 1：菜单 333 现只应剩 角色 1(admin) / 28(PRODUCTION 全权限) / 34(QUALITY 品质主管)
SELECT '核验1：菜单333 剩余授权' AS check_point;
SELECT r.role_id, r.role_name, r.role_key, m.menu_id, m.menu_name
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.menu_id = 333
ORDER BY r.role_id;

-- 核验 2：23/24 不再持有任何 quality:ncr:* 权限（页面与处置都不再可见）
SELECT '核验2：23/24 的 quality:ncr* 权限（应为空）' AS check_point;
SELECT r.role_id, r.role_name, m.menu_id, m.menu_name, m.perms
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id IN (23, 24)
  AND m.perms LIKE 'quality:ncr%'
ORDER BY r.role_id, m.menu_id;
