-- dev-20260828-040 补齐角色菜单授权中缺失的祖先菜单。
-- 本迁移可重复执行；递归仅沿 sys_menu 中真实存在的 parent_id 关系向上追溯。

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
WITH RECURSIVE authorized_menu_ancestors AS (
  SELECT rm.role_id, menu.menu_id, menu.parent_id
  FROM sys_role_menu rm
  JOIN sys_menu menu ON menu.menu_id = rm.menu_id

  UNION DISTINCT

  SELECT ancestors.role_id, parent.menu_id, parent.parent_id
  FROM authorized_menu_ancestors ancestors
  JOIN sys_menu parent ON parent.menu_id = ancestors.parent_id
  WHERE ancestors.parent_id <> 0
)
SELECT role_id, menu_id
FROM authorized_menu_ancestors;

-- 回归检测：迁移后以下查询应返回 0 行。
-- SELECT rm.role_id, c.parent_id
-- FROM sys_role_menu rm
-- JOIN sys_menu c ON c.menu_id = rm.menu_id
-- JOIN sys_menu p ON p.menu_id = c.parent_id
-- WHERE c.parent_id <> 0
--   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm2
--                   WHERE rm2.role_id = rm.role_id AND rm2.menu_id = c.parent_id);
