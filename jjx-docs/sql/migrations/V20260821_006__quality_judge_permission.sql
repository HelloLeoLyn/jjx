-- =====================================================
-- V20260821_006：质检判定权限点 production:quality:judge（TT-UI-04）
-- 定位：质检判定权限过宽——judge/reinspect 无独立判定权限点，
--       任何持有 production:quality:view 的角色都能 PASS/FAIL。
--       新增 F 按钮「质检判定」（production:quality:judge，挂 264 质检管理），
--       后端 judge/reinspect 加 @SaCheckPermission("production:quality:judge")，
--       前端判定/复检按钮 v-hasPermi("production:quality:judge")。
-- 角色授权：admin(1) / 28 生产全权限 / 29 生产业务操作（当前 quality:view 持有者；
--           30 车间主任 / 31 班组长 / 32 操作工 无质检菜单，不授予）
-- 幂等：按钮 INSERT ... WHERE NOT EXISTS；授权 INSERT IGNORE，重复执行结果一致。
-- =====================================================

-- ---------- 1) 264 质检管理 下新增 F 按钮「质检判定」 ----------
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '质检判定', 264, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:quality:judge', NULL, '0,43,264', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), '判定 PASS/FAIL + 复检（P3-C 正式质量动作）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:quality:judge');

-- ---------- 2) 角色授权：1/28/29（当前 quality:view 持有者） ----------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 29) r
WHERE m.perms = 'production:quality:judge';
