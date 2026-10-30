-- 任务码：dev-20261030-009
-- 目的：分类树同级 sort_order 收敛为唯一序号（每级 10、20、30…），消除大量并列导致的随机顺序。
-- 依据：下拉/级联按树序展示，同级并列会让顺序退化成 id 顺序。
-- 影响面：只改 inventory_material_category.sort_order，结构不变。

UPDATE inventory_material_category c
JOIN (
  SELECT category_id,
         ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY sort_order, category_id) AS rn
  FROM inventory_material_category
) t ON t.category_id = c.category_id
SET c.sort_order = t.rn * 10;

SELECT parent_id, GROUP_CONCAT(CONCAT(category_name, '(', sort_order, ')') ORDER BY sort_order SEPARATOR ' ') AS 同级顺序
FROM inventory_material_category
GROUP BY parent_id
ORDER BY parent_id;
