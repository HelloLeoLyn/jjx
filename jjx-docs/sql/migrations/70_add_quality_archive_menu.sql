-- dev-20260908-020：文档管理增加“质量记录归档”页面菜单，授权随父目录复制。

SET @quality_archive_order_num := (
    SELECT COALESCE(MAX(order_num), 0) + 1
    FROM sys_menu
    WHERE parent_id = 316
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark
)
SELECT
    '质量记录归档', 316, @quality_archive_order_num,
    'quality-archive', 'views/production/quality-archive/index.vue', NULL,
    '1', '0', 'C', '0', '0', 'production:quality-template:view', 'FolderChecked',
    '0,316', 'ProductionQualityArchive', '1', NULL, @quality_archive_order_num,
    'Codex', NOW(), 'Codex', NOW(), 'dev-20260908-020'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE parent_id = 316
      AND (path = 'quality-archive' OR menu_name = '质量记录归档')
);

SET @quality_archive_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 316
      AND (path = 'quality-archive' OR menu_name = '质量记录归档')
    ORDER BY CASE WHEN path = 'quality-archive' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT parent_role.role_id, @quality_archive_menu_id
FROM (SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id = 316) parent_role
WHERE @quality_archive_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = parent_role.role_id
        AND existing.menu_id = @quality_archive_menu_id
  );
