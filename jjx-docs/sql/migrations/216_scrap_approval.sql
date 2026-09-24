-- risk: low
-- dev-20260924-005（报废线二期）：报废授权 —— 新增按钮级权限 quality:ncr:scrap-approve + 审批件数阈值配置。
-- Tables: sys_menu, sys_role_menu, sys_config
-- 口径：报废件数 ≤ 阈值一步到底（现状）；> 阈值进入「待审批」，品质主管审批通过才计入台账与件级。
-- 幂等：全部按存在性判定插入，可重复执行。

-- ① 按钮级权限（挂「产品不良台账 378」下，与「不良处置 386 / 撤销 396」同层）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth,
                      redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '报废审批', 378, 3, '', NULL, NULL, 1, 0, 'F', 0, 0,
       'quality:ncr:scrap-approve', NULL, '0,338,378', NULL, 1, NULL, 0,
       'Hermes', NOW(), 'Hermes', NOW(), 'dev-20260924-005'
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'quality:ncr:scrap-approve') existing
);

-- ② 授权：超级管理员（1）+ 品质主管（34）—— 职责分离，不给生产全权限（28）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 34) r
JOIN (SELECT menu_id FROM sys_menu WHERE perms = 'quality:ncr:scrap-approve' LIMIT 1) m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ③ 审批阈值配置（缺省 5：≤5 件一步到底，>5 件需审批）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
SELECT 'quality.ncr.scrap.approval-threshold', '5',
       '报废审批件数阈值（超过该值需品质主管审批）', 'quality',
       'dev-20260924-005：≤阈值一步到底；>阈值进入待审批，审批通过才计入台账', 0, 1
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT config_key FROM sys_config WHERE config_key = 'quality.ncr.scrap.approval-threshold') existing
);
