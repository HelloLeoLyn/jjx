-- 印刷齐套菜单（dev-20261126-002）
INSERT INTO sys_menu(parent_id, menu_name, order_num, path, component, menu_type, visible, status, perms, icon, ancestors, create_by, create_time)
SELECT 43, '印刷齐套', 9, 'printing-kit', 'views/production/printing-kit/index.vue', 'C', '0', '0',
       'production:operation-execution:view', 'List', '0,43', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id=43 AND path='printing-kit');

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 32) r
JOIN sys_menu m ON m.parent_id=43 AND m.path='printing-kit'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.role_id AND x.menu_id=m.menu_id);

SELECT menu_id, parent_id, menu_name, path, perms FROM sys_menu WHERE parent_id=43 AND path='printing-kit';
