-- dev-20260828-042 修复物料按钮菜单的孤儿父节点。
-- 本迁移可重复执行；找不到物料列表菜单时不会更新任何记录。

-- 先修复物料列表菜单自身的祖先链和路由名。祖先链从其父菜单动态推导，
-- 当父菜单的 ancestors 也缺失时以根节点 0 兜底，避免生成 NULL 或前导逗号。
UPDATE sys_menu material
JOIN (
  SELECT menu_id
  FROM sys_menu
  WHERE menu_type = 'C'
    AND (route_name = 'MaterialList'
         OR component = 'views/inventory/material/index.vue')
  ORDER BY CASE WHEN route_name = 'MaterialList' THEN 0 ELSE 1 END, menu_id
  LIMIT 1
) target ON target.menu_id = material.menu_id
JOIN sys_menu material_parent ON material_parent.menu_id = material.parent_id
SET material.ancestors = CASE
      WHEN material.ancestors IS NULL OR TRIM(material.ancestors) = ''
        THEN CONCAT(
          COALESCE(NULLIF(TRIM(material_parent.ancestors), ''), '0'),
          ',', material_parent.menu_id
        )
      ELSE material.ancestors
    END,
    material.route_name = CASE
      WHEN material.route_name IS NULL OR TRIM(material.route_name) = ''
        THEN 'MaterialList'
      ELSE material.route_name
    END
WHERE material.ancestors IS NULL
   OR TRIM(material.ancestors) = ''
   OR material.route_name IS NULL
   OR TRIM(material.route_name) = '';

UPDATE sys_menu child
JOIN (
  SELECT menu_id, ancestors
  FROM sys_menu
  WHERE menu_type = 'C'
    AND (route_name = 'MaterialList'
         OR component = 'views/inventory/material/index.vue')
  ORDER BY CASE WHEN route_name = 'MaterialList' THEN 0 ELSE 1 END, menu_id
  LIMIT 1
) parent
SET child.parent_id = parent.menu_id,
    child.ancestors = COALESCE(
      CONCAT(NULLIF(TRIM(parent.ancestors), ''), ',', parent.menu_id),
      CONCAT('0,', parent.menu_id)
    )
WHERE child.menu_id IN (110, 111, 112);

-- 孤儿检测：迁移后以下查询应返回 0 行。
-- SELECT m.menu_id, m.menu_name, m.parent_id
-- FROM sys_menu m LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
-- WHERE m.parent_id <> 0 AND p.menu_id IS NULL;

-- 缺失祖先授权检测：迁移后以下查询应返回 0 行。
-- SELECT rm.role_id, c.parent_id
-- FROM sys_role_menu rm
-- JOIN sys_menu c ON c.menu_id = rm.menu_id
-- JOIN sys_menu p ON p.menu_id = c.parent_id
-- WHERE c.parent_id <> 0
--   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm2
--                   WHERE rm2.role_id = rm.role_id AND rm2.menu_id = c.parent_id);

-- 非法祖先链检测：迁移后以下查询应返回 0 行。
-- SELECT menu_id, menu_name, parent_id, ancestors
-- FROM sys_menu
-- WHERE ancestors IS NULL OR ancestors NOT LIKE '0%';
