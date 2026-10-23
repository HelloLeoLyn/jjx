-- dev-20261024-001：物料主数据新模型只读检查。执行前先完成303/304迁移。
-- 危险等级：只读；不补值、不纠偏、不修改流水。完整属性类型/必填检查另由 /inventory/material/master/audit 返回。

SELECT COUNT(*) AS total,
       SUM(original_name IS NULL) AS pending_migration,
       SUM(original_name IS NOT NULL) AS new_model
FROM inventory_material;

SELECT c.category_code,c.category_name,COUNT(*) AS material_count
FROM inventory_material m
LEFT JOIN inventory_material_category c ON c.category_id=m.category_id
WHERE m.original_name IS NOT NULL
GROUP BY c.category_code,c.category_name;

SELECT COUNT(*) AS new_model_missing_category_or_template
FROM inventory_material m
LEFT JOIN inventory_material_category c ON c.category_id=m.category_id
LEFT JOIN inventory_attribute_template t ON t.category_id=c.category_id
WHERE m.original_name IS NOT NULL
  AND (c.category_id IS NULL OR t.template_id IS NULL OR t.status<>1);

SELECT COUNT(*) AS new_model_on_nonleaf_category
FROM inventory_material m
WHERE m.original_name IS NOT NULL
  AND EXISTS (SELECT 1 FROM inventory_material_category child WHERE child.parent_id=m.category_id);

SELECT COUNT(*) AS new_model_invalid_flags_or_empty_attributes
FROM inventory_material
WHERE original_name IS NOT NULL
  AND (is_customer_supplied NOT IN (0,1) OR is_sample NOT IN (0,1)
       OR attribute_json IS NULL OR JSON_TYPE(attribute_json)<>'OBJECT');

SELECT m.material_id,m.original_name,m.material_meta
FROM inventory_material m
JOIN inventory_attribute_template t ON t.category_id=m.category_id
WHERE m.original_name IS NOT NULL AND m.material_meta IS NOT NULL
  AND JSON_CONTAINS(JSON_EXTRACT(t.attribute_schema,'$.meta_options'),JSON_QUOTE(m.material_meta))=0;

SELECT m.material_id,attributes.attr_key AS unknown_attribute
FROM inventory_material m
JOIN inventory_attribute_template t ON t.category_id=m.category_id
JOIN JSON_TABLE(JSON_KEYS(m.attribute_json),'$[*]' COLUMNS(attr_key VARCHAR(50) PATH '$')) attributes
WHERE m.original_name IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(t.attribute_schema,'$.fields[*]' COLUMNS(field_key VARCHAR(50) PATH '$.key')) fields WHERE fields.field_key=attributes.attr_key);

WITH RECURSIVE category_tree AS (
  SELECT category_id,category_code,category_level,
         CAST(category_code AS CHAR(500) CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci AS visited,
         (CASE category_code WHEN 'RAW' THEN 'R' WHEN 'SEMI' THEN 'S' WHEN 'FINISHED' THEN 'F' END) COLLATE utf8mb4_unicode_ci AS root_type,1 AS depth
  FROM inventory_material_category
  WHERE category_code IN ('RAW','SEMI','FINISHED') AND (parent_id IS NULL OR parent_id=0)
  UNION ALL
  SELECT c.category_id,c.category_code,c.category_level,CONCAT(p.visited,',',c.category_code),p.root_type,p.depth+1
  FROM inventory_material_category c JOIN category_tree p ON c.parent_id=p.category_id
  WHERE p.depth<3 AND FIND_IN_SET(c.category_code,p.visited)=0
)
SELECT m.material_id,m.material_type,t.root_type,t.depth,t.category_level
FROM inventory_material m LEFT JOIN category_tree t ON t.category_id=m.category_id
WHERE m.original_name IS NOT NULL
  AND (t.category_id IS NULL OR NOT (m.material_type<=>t.root_type) OR t.depth<>t.category_level);
