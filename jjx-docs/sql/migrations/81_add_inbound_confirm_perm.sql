-- dev-20260910-010：生产完工入库改为人工确认后过账，新增独立“确认入库”按钮权限。
-- 原编辑/审核权限无法准确表达库存过账职责，因此拆分 inventory:inbound:confirm 供仓库角色配置。

SET @inbound_confirm_order_num := (
    SELECT COALESCE(MAX(order_num), 0) + 1
    FROM sys_menu
    WHERE parent_id = 28
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark
)
SELECT
    '确认入库', 28, @inbound_confirm_order_num, '', NULL, NULL,
    '1', '0', 'F', '0', '0', 'inventory:inbound:confirm', NULL,
    '0,18,28', NULL, '1', NULL, 0,
    'Codex', NOW(), 'Codex', NOW(), 'dev-20260910-010'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE perms = 'inventory:inbound:confirm'
);

SET @inbound_confirm_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'inventory:inbound:confirm'
    ORDER BY menu_id
    LIMIT 1
);

-- 目标仓库角色缺少入库作业父目录授权时反向补齐，避免按钮有权但菜单入口不可见。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, 28
FROM sys_role role
WHERE role.role_key IN ('inventory:ops', 'inventory:all')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.role_id = role.role_id
        AND existing.menu_id = 28
  );

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, @inbound_confirm_menu_id
FROM sys_role role
WHERE role.role_key IN ('inventory:ops', 'inventory:all')
  AND @inbound_confirm_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.role_id = role.role_id
        AND existing.menu_id = @inbound_confirm_menu_id
  );
