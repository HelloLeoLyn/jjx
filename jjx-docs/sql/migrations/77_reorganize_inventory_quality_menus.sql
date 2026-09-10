-- dev-20260909-010：库存菜单按职责分组，IQC 入口归入生产质量域。
-- 子菜单使用绝对 path，保持现有页面 URL 和代码跳转不变。

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '基础资料', 18, 1, 'base-data', NULL, NULL, '1', '0', 'M', '0', '0', NULL, 'Files',
    '0,18', 'InventoryBaseData', '1', NULL, 1, 'Codex', NOW(), 'Codex', NOW(), 'dev-20260909-010'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 18 AND path = 'base-data');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '库存管控', 18, 2, 'stock-control', NULL, NULL, '1', '0', 'M', '0', '0', NULL, 'DataAnalysis',
    '0,18', 'InventoryStockControl', '1', NULL, 2, 'Codex', NOW(), 'Codex', NOW(), 'dev-20260909-010'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 18 AND path = 'stock-control');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '库内作业', 18, 4, 'warehouse-operations', NULL, NULL, '1', '0', 'M', '0', '0', NULL, 'Operation',
    '0,18', 'InventoryWarehouseOperations', '1', NULL, 4, 'Codex', NOW(), 'Codex', NOW(), 'dev-20260909-010'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 18 AND path = 'warehouse-operations');

SET @base_menu_id := (SELECT menu_id FROM sys_menu WHERE parent_id = 18 AND path = 'base-data' LIMIT 1);
SET @stock_menu_id := (SELECT menu_id FROM sys_menu WHERE parent_id = 18 AND path = 'stock-control' LIMIT 1);
SET @ops_menu_id := (SELECT menu_id FROM sys_menu WHERE parent_id = 18 AND path = 'warehouse-operations' LIMIT 1);

UPDATE sys_menu SET parent_id = @base_menu_id, order_num = 1, path = '/inventory/material',
    ancestors = CONCAT('0,18,', @base_menu_id), update_by = 'Codex', update_time = NOW()
WHERE menu_id = 19;
UPDATE sys_menu SET parent_id = @base_menu_id, order_num = 2, path = '/inventory/warehouse',
    ancestors = CONCAT('0,18,', @base_menu_id), update_by = 'Codex', update_time = NOW()
WHERE menu_id = 23;
UPDATE sys_menu SET ancestors = CONCAT('0,18,', @base_menu_id, ',19'), update_by = 'Codex', update_time = NOW()
WHERE parent_id = 19;
UPDATE sys_menu SET ancestors = CONCAT('0,18,', @base_menu_id, ',23'), update_by = 'Codex', update_time = NOW()
WHERE parent_id = 23;

UPDATE sys_menu SET parent_id = @stock_menu_id, order_num = 1, path = '/inventory/stock',
    ancestors = CONCAT('0,18,', @stock_menu_id), update_by = 'Codex', update_time = NOW()
WHERE menu_id = 26;
UPDATE sys_menu SET parent_id = @stock_menu_id, order_num = 2, path = '/inventory/alert',
    ancestors = CONCAT('0,18,', @stock_menu_id), update_by = 'Codex', update_time = NOW()
WHERE menu_id = 27;

UPDATE sys_menu SET menu_name = '出入库作业', order_num = 3, sort = 3,
    update_by = 'Codex', update_time = NOW(), remark = 'dev-20260909-010'
WHERE menu_id = 242;

UPDATE sys_menu SET parent_id = @ops_menu_id, order_num = 1, path = '/inventory/stocktake',
    ancestors = CONCAT('0,18,', @ops_menu_id), update_by = 'Codex', update_time = NOW()
WHERE menu_id = 34;
UPDATE sys_menu SET parent_id = @ops_menu_id, order_num = 2, path = '/inventory/transfer',
    ancestors = CONCAT('0,18,', @ops_menu_id), update_by = 'Codex', update_time = NOW()
WHERE menu_id = 35;

-- IQC 属于质量域；保留 /inventory/iqc URL，避免既有书签与跳转失效。
UPDATE sys_menu SET parent_id = 43, order_num = 5, path = '/inventory/iqc', ancestors = '0,43',
    update_by = 'Codex', update_time = NOW(), remark = 'dev-20260909-010'
WHERE menu_id = 330;
UPDATE sys_menu SET parent_id = 43, order_num = 6, path = '/inventory/iqc-quarantine', ancestors = '0,43',
    update_by = 'Codex', update_time = NOW(), remark = 'dev-20260909-010'
WHERE menu_id = 333;

-- 新目录继承其子菜单已有角色，避免重组后父目录缺权导致入口消失。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT child_role.role_id, parent_menu.menu_id
FROM (
    SELECT role_id, @base_menu_id AS menu_id FROM sys_role_menu WHERE menu_id IN (19, 23)
    UNION ALL SELECT role_id, @stock_menu_id FROM sys_role_menu WHERE menu_id IN (26, 27)
    UNION ALL SELECT role_id, @ops_menu_id FROM sys_role_menu WHERE menu_id IN (34, 35)
) child_role
JOIN sys_menu parent_menu ON parent_menu.menu_id = child_role.menu_id
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = child_role.role_id AND existing.menu_id = parent_menu.menu_id
);

-- IQC 角色同时获得生产管理父目录；已有授权保持不变。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT iqc_role.role_id, 43
FROM sys_role_menu iqc_role
WHERE iqc_role.menu_id IN (330, 333)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = iqc_role.role_id AND existing.menu_id = 43);
