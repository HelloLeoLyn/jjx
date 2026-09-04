-- ============================================================
-- 移动端权限补配（2026-09-04 定案，2026-09-05 执行）
-- 背景：移动端实跑发现 3 个权限缺口（mobile-business-review-20260904.md）
--   ① work-report:view 操作工(32)缺 → 报工列表 403
--   ② quality:judge 权限点未注册 → 质检判定 403
--   ③ outbound:add/view 生产角色全无 → 领料 403
-- 定案：view 补授 32；judge 注册并授 28/29；outbound 授 28(add+view)/32(仅 view)
-- ============================================================

-- ② 注册 quality:judge 权限点（挂质检管理 264 下，F 按钮型，隐藏）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      ancestors, requires_auth, sort, create_by)
SELECT 328, '检验判定', 264, 2, '', NULL,
       1, 0, 'F', 0, 0, 'production:quality:judge', '#',
       '0,43,264', 1, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 328);

-- ① work-report:view(292) → 操作工 32
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(32, 292);

-- ② quality:judge(328) → 28(生产全权限)/29(生产业务操作)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(28, 328),
(29, 328);

-- ③ outbound → 28 全权限(add+view)；32 仅 view（领料提交=管理动作，操作工不授 add）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(28, 33),   -- inventory:outbound:view
(28, 131),  -- inventory:outbound:add
(32, 33);   -- inventory:outbound:view

-- 验证：
-- SELECT rm.role_id, r.role_name, m.perms
-- FROM sys_role_menu rm JOIN sys_role r ON rm.role_id=r.role_id
-- JOIN sys_menu m ON rm.menu_id=m.menu_id
-- WHERE rm.role_id IN (28,29,32) AND (m.perms LIKE '%work-report%' OR m.perms LIKE '%quality%' OR m.perms LIKE '%outbound%')
-- ORDER BY rm.role_id, m.perms;
