-- dev-20260908-004：增加 IQC 隔离台账可见菜单入口。
-- 幂等：按 parent_id + path 判断；权限继承现有 IQC 进料检测(menu_id=330)的角色授权。

SET @iqc_quarantine_exists := (
    SELECT COUNT(*) FROM sys_menu WHERE parent_id = 18 AND path = 'iqc-quarantine'
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark
)
SELECT
    'IQC隔离台账', 18, 261, 'iqc-quarantine', 'views/inventory/iqc-quarantine/index.vue', NULL,
    '1', '0', 'C', '0', '0', 'inventory:inbound:view', 'Warning',
    '0,18', 'InventoryIqcQuarantineLedger', '1', NULL, 261,
    'Codex', NOW(), 'Codex', NOW(), 'dev-20260908-004'
WHERE @iqc_quarantine_exists = 0;

SET @iqc_quarantine_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 18 AND path = 'iqc-quarantine'
    ORDER BY menu_id DESC LIMIT 1
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT source_role.role_id, @iqc_quarantine_menu_id
FROM (SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id = 330) source_role
WHERE @iqc_quarantine_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = source_role.role_id
        AND existing.menu_id = @iqc_quarantine_menu_id
  );
