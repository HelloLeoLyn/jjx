-- risk: low
-- dev-20260924-002（剩余半张）：通用工单补料（超耗 / 报废补产）独立开单权限点。
-- 内容：新增按钮级权限 inventory:outbound:supplement（挂「出库作业」33 下），并授予与「返工补料 398」同范围的角色。
-- 幂等：先按 perms 判存在插菜单，再按 (role_id, menu_id) 判存在授权；重复执行不新增行。

-- ① 菜单（按钮级）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth,
                      redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工单补料', 33, 6, '', NULL, NULL, 1, 0, 'F', 0, 0,
       'inventory:outbound:supplement', NULL, '0,18,33', NULL, 1, NULL, 0,
       'Codex', NOW(), 'Codex', NOW(), 'dev-20260924-002'
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'inventory:outbound:supplement') existing
);

-- ② 授权（与返工补料 398 同范围：超级管理员 / INVENTORY 全权限 / QUALITY 品质主管）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 22 UNION ALL SELECT 34) r
JOIN (SELECT menu_id FROM sys_menu WHERE perms = 'inventory:outbound:supplement' LIMIT 1) m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);
