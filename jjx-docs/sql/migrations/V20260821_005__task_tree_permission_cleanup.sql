-- =====================================================
-- V20260821_005：Task Tree 角色权限矩阵（TT-UI-03，仅矩阵）
-- 定位：在 V20260821_003（TT-UI-01 权限体系）+ V20260821_004（TT-UI-02 菜单收敛）之后，
--       按已确认业务重校 production:task:*（菜单 261 派工管理子树）的角色授权矩阵。
-- 范围：只维护 sys_role_menu 的 task 权限行（menu_id = 261 及其 5 个按钮）；
--       报工（production:work-report:*）单独维护，不在本迁移内改动。
-- 最终矩阵（role_id → role_name → task 权限）：
--   1  超级管理员(admin)                      view + dispatch + assign + recall + return + admin
--   28 PRODUCTION 全权限(生产管理员/派工主管)  view + dispatch + assign + recall + return（无 admin）
--   29 PRODUCTION 业务操作(生产业务操作)      view + assign + recall + return（无 dispatch/admin）
--   30 PRODUCTION 派工主管(车间主任)          view + assign + recall + return（无 dispatch/admin）
--   31 PRODUCTION 班组长                      view + assign + recall + return（无 dispatch/admin）
--   32 PRODUCTION 操作工                      view（无 dispatch/assign/recall/return/admin）
-- 说明：
--   - 29 生产业务操作在 P1 派工模型即持有 261/262/263（view/assign/start），
--     "无任何 task 权限"不符合已确认业务；按车间主任/班组长同级的业务操作收敛
--     （29 持有人即 zhuren_*/bzz_*，不得因此获得 dispatch/admin）。
--   - 操作工报工单独走 production:work-report:add（本人可撤，cancel 保留），
--     本迁移不改 work-report 授权。
--   - RBAC 只是动作能力；实际 assign/recall/return 仍由 TaskNodeService
--     校验本人/父节点持有关系（身份边界不改，本迁移不触碰 Service）。
-- 幂等：每角色先清 261 子树旧关系、再按矩阵 INSERT IGNORE 重建（同 V20260821_003 §4 模式）。
-- =====================================================

-- ---------- 1) admin(1) 超级管理员：全部 6 个 task 权限 ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 1 AND (m.menu_id = 261 OR m.parent_id = 261);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id = 261 OR parent_id = 261;

-- ---------- 2) 28 生产管理员/派工主管：view + dispatch + assign + recall + return（无 admin） ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 28 AND (m.menu_id = 261 OR m.parent_id = 261);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 28, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view', 'production:task:dispatch', 'production:task:assign', 'production:task:recall', 'production:task:return');

-- ---------- 3) 29 生产业务操作：view + assign + recall + return（无 dispatch/admin） ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 29 AND (m.menu_id = 261 OR m.parent_id = 261);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 29, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view', 'production:task:assign', 'production:task:recall', 'production:task:return');

-- ---------- 4) 30 车间主任（角色名 PRODUCTION 派工主管）：view + assign + recall + return（无 dispatch/admin） ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 30 AND (m.menu_id = 261 OR m.parent_id = 261);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 30, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view', 'production:task:assign', 'production:task:recall', 'production:task:return');

-- ---------- 5) 31 班组长：view + assign + recall + return（无 dispatch/admin） ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 31 AND (m.menu_id = 261 OR m.parent_id = 261);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 31, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view', 'production:task:assign', 'production:task:recall', 'production:task:return');

-- ---------- 6) 32 操作工：仅 view（无 dispatch/assign/recall/return/admin）；报工走 work-report（不在此维护） ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 32 AND (m.menu_id = 261 OR m.parent_id = 261);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 32, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261) AND perms = 'production:task:view';
