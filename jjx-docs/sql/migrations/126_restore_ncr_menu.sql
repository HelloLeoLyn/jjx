-- ============================================================================
-- 126: 恢复「不良台账」菜单（menu_id=378）——dev-20260917-010
-- 现象：sys_menu 中 378 行丢失，但 sys_role_menu 仍保留 376~386 的授权、按钮 386(不良处置) 的 parent_id=378 成为孤儿
--       → 质量管理下"不良台账"页面入口消失。
-- 处理：按原 menu_id=378 原样恢复（保持与既有授权/父子关系一致），并顺手补齐 icon（避免再落 '#' 默认值）。
-- 幂等：INSERT ... ON DUPLICATE KEY UPDATE。
-- ============================================================================
USE `jjx_erp_db`;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, requires_auth, create_by, create_time, remark)
VALUES
    (378, '不良台账', 338, 7, '/quality/ncr', 'views/quality/ncr/index.vue', 0, 0, 'C', 0, 0, 'quality:ncr:view', 'Warning', '0,338', 1, 'Hermes', NOW(), '质量重构：不良台账（返工/让步接收/报废）')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    component = VALUES(component),
    perms = VALUES(perms),
    icon = VALUES(icon),
    visible = VALUES(visible),
    status = VALUES(status),
    update_time = NOW();

SELECT '核验：质量管理(338)下菜单与按钮' AS check_point;
SELECT menu_id, menu_name, parent_id, menu_type, icon, path, perms FROM sys_menu WHERE parent_id = 338 OR parent_id IN (376, 377, 378) ORDER BY menu_id;
SELECT '核验：父级缺失的孤儿菜单（应为 0）' AS check_point;
SELECT COUNT(*) AS orphan_rows FROM sys_menu c WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
  AND c.parent_id NOT IN (SELECT menu_id FROM (SELECT menu_id FROM sys_menu) p);
SELECT '核验：C 类菜单 icon 仍为 # 的行（应为 0）' AS check_point;
SELECT COUNT(*) AS c_menu_hash_icon FROM sys_menu WHERE menu_type = 'C' AND icon = '#';
