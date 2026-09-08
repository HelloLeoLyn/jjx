-- dev-20260908-004：库存管理增加“IQC 隔离台账”页面菜单入口。
-- 幂等键使用 parent_id + path；同一权限 inventory:inbound:view 已被多个库存菜单复用，不能单独作为防重键。

SET @iqc_quarantine_order_num := (
    SELECT COALESCE(MAX(order_num), 0) + 1
    FROM sys_menu
    WHERE parent_id = 18
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark
)
SELECT
    'IQC 隔离台账', 18, @iqc_quarantine_order_num,
    'iqc-quarantine', 'views/inventory/iqc-quarantine/index.vue', NULL,
    '1', '0', 'C', '0', '0', 'inventory:inbound:view', 'Warning',
    '0,18', 'InventoryIqcQuarantineLedger', '1', NULL, @iqc_quarantine_order_num,
    'Codex', NOW(), 'Codex', NOW(), 'dev-20260908-004'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE parent_id = 18
      AND (path = 'iqc-quarantine' OR menu_name = 'IQC 隔离台账')
);

SET @menuId := (
    SELECT menu_id
    FROM sys_menu
    WHERE parent_id = 18
      AND (path = 'iqc-quarantine' OR menu_name = 'IQC 隔离台账')
    ORDER BY CASE WHEN path = 'iqc-quarantine' THEN 0 ELSE 1 END, menu_id
    LIMIT 1
);

-- 兼容此前脚本已落库的无空格菜单名，并确保页面菜单字段与当前实现一致。
UPDATE sys_menu
SET menu_name = 'IQC 隔离台账',
    path = 'iqc-quarantine',
    component = 'views/inventory/iqc-quarantine/index.vue',
    menu_type = 'C',
    visible = '0',
    status = '0',
    perms = 'inventory:inbound:view',
    ancestors = '0,18',
    route_name = 'InventoryIqcQuarantineLedger',
    requires_auth = '1',
    update_by = 'Codex',
    update_time = NOW(),
    remark = 'dev-20260908-004'
WHERE menu_id = @menuId;

-- 授权随库存管理父目录复制。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT parent_role.role_id, @menuId
FROM (
    SELECT DISTINCT role_id
    FROM sys_role_menu
    WHERE menu_id = 18
) parent_role
WHERE @menuId IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.role_id = parent_role.role_id
        AND existing.menu_id = @menuId
  );
