-- dev-20260901 质量记录模板菜单归位：生产管理 → 文档管理（文控）
-- 依据：质量记录一览表属文控台账（ISO 惯例 + 表内 owner_dept 存在"文控"19 条），非生产管理职责
-- 幂等：目录不存在才建；菜单存在才迁

-- 1) 新建"文档管理"顶级目录
INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
   perms, icon, ancestors, route_name, requires_auth, sort, create_by, create_time, update_by, remark)
SELECT '文档管理', 0, 800, 'doc', 'layout/index.vue', '1', '0', 'M', '0', '0',
       '', 'Folder', '0', 'DocCenter', '1', 800, 'admin', NOW(), 'admin', '文控中心（dev-20260901）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_name = 'DocCenter');

-- 2) 质量记录模板菜单迁入文档管理
UPDATE sys_menu m
JOIN sys_menu d ON d.route_name = 'DocCenter'
SET m.parent_id = d.menu_id,
    m.ancestors = CONCAT(d.ancestors, ',', d.menu_id),
    m.order_num = 1
WHERE m.perms = 'production:quality-template:view' AND m.parent_id = 43;

-- 3) 角色授权：目录授权继承自子菜单（有子菜单授权的角色补齐目录授权）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, d.menu_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id AND m.perms = 'production:quality-template:view'
JOIN sys_menu d ON d.route_name = 'DocCenter';
