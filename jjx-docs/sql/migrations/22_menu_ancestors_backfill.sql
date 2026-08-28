-- dev-20260828-045 全库重算 sys_menu.ancestors。
-- 本迁移可重复执行；仅回写从根节点可完整到达的菜单，断链及环路中的菜单保持不变。

WITH RECURSIVE canonical_menu_ancestors AS (
  SELECT root.menu_id,
         root.parent_id,
         CAST('0' AS CHAR(2000)) AS ancestors,
         0 AS depth,
         CAST(CONCAT(',', root.menu_id, ',') AS CHAR(2000)) AS visited_path
  FROM sys_menu root
  WHERE root.parent_id = 0

  UNION ALL

  SELECT child.menu_id,
         child.parent_id,
         CONCAT(parent.ancestors, ',', parent.menu_id) AS ancestors,
         parent.depth + 1 AS depth,
         CONCAT(parent.visited_path, child.menu_id, ',') AS visited_path
  FROM canonical_menu_ancestors parent
  JOIN sys_menu child ON child.parent_id = parent.menu_id
  WHERE parent.depth < 100
    AND LOCATE(CONCAT(',', child.menu_id, ','), parent.visited_path) = 0
)
UPDATE sys_menu menu
JOIN canonical_menu_ancestors canonical ON canonical.menu_id = menu.menu_id
SET menu.ancestors = canonical.ancestors;

-- 规范性检测：迁移后以下查询应返回 0 行。
-- SELECT menu_id, menu_name, parent_id, ancestors
-- FROM sys_menu
-- WHERE ancestors IS NULL OR ancestors NOT LIKE '0%';

-- 与父链一致性检测：迁移后以下查询应返回 0 行。
-- SELECT c.menu_id, c.ancestors, p.ancestors AS parent_ancestors
-- FROM sys_menu c JOIN sys_menu p ON p.menu_id = c.parent_id
-- WHERE c.parent_id <> 0
--   AND c.ancestors <> CONCAT(p.ancestors, ',', p.menu_id);

-- 孤儿检测：迁移后以下查询应返回 0 行，作为前置条件复核。
-- SELECT m.menu_id FROM sys_menu m
-- LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
-- WHERE m.parent_id <> 0 AND p.menu_id IS NULL;
