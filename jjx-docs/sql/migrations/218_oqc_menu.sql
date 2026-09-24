-- risk: low
-- dev-20260921-042（OQC 出货检验一期）：补「出货检验」菜单入口。
-- 背景：OQC 检验批/抽样方案/页面壳已存在（发货时按明细自动建 OQC 批、签收守卫要求最新版 OQC 判定合格），
--       缺的是质检员的入口菜单（核查稿：质量管理子树无 OQC）。
-- 口径：检验批模型/检验项目/缺陷记录/件级追溯以既有模型为唯一出处（dev-20260924-004），本卡只加菜单与建批口径。
-- 幂等：按 perms 判定插入。

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth,
                      redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '出货检验', 338, 6, '/quality/lot/oqc', 'views/quality/lot/oqc-lot.vue', NULL, 1, 0,
       'C', 0, 0, 'quality:lot:view', 'Van', '0,338', NULL, 1, NULL, 0,
       'Hermes', NOW(), 'Hermes', NOW(), 'dev-20260921-042'
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE path = '/quality/lot/oqc') existing
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 33 UNION ALL SELECT 34) r
JOIN (SELECT menu_id FROM sys_menu WHERE path = '/quality/lot/oqc' LIMIT 1) m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);
