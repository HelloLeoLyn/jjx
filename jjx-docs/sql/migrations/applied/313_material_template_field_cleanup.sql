-- 任务码：dev-20261028-002
-- 目的：修正 312 的残留——312 用 JSON_SEARCH 路径只删掉了 thickness_mm 字段对象里的 key，
--       留下 {label:"厚度",type:"decimal",...} 这种无 key 的孤儿字段。本迁移按字段对象整体重建 fields 数组。
-- 口径：只保留带 key 且 key <> 'thickness_mm' 的字段，顺序按原 ORDINALITY 保持。
-- 实现：JSON_TABLE 取字段后 GROUP_CONCAT（JSON_ARRAYAGG 不支持 ORDER BY，临时表在同一语句不能重开）。
-- 影响面：仅 inventory_attribute_template.attribute_schema.fields。
-- 回滚：从 20261028-1115 全库备份恢复该表即可。

SET SESSION group_concat_max_len = 1000000;

UPDATE inventory_attribute_template t
SET t.attribute_schema = JSON_SET(t.attribute_schema, '$.fields', CAST(CONCAT('[', COALESCE((
      SELECT GROUP_CONCAT(JSON_UNQUOTE(jt.item) ORDER BY jt.ord SEPARATOR ',')
      FROM JSON_TABLE(t.attribute_schema, '$.fields[*]' COLUMNS (
            ord FOR ORDINALITY,
            k VARCHAR(64) PATH '$.key',
            item JSON PATH '$')) jt
      WHERE jt.k IS NOT NULL AND jt.k <> '' AND jt.k <> 'thickness_mm'
    ), ''), ']') AS JSON))
WHERE EXISTS (
  SELECT 1
  FROM JSON_TABLE(t.attribute_schema, '$.fields[*]' COLUMNS (k2 VARCHAR(64) PATH '$.key')) j2
  WHERE j2.k2 IS NULL OR j2.k2 = '' OR j2.k2 = 'thickness_mm');

SELECT COUNT(*) AS 仍含厚度字段
FROM inventory_attribute_template
WHERE JSON_SEARCH(attribute_schema, 'one', 'thickness_mm') IS NOT NULL;

SELECT COUNT(*) AS 无key的孤儿字段模板
FROM inventory_attribute_template t
WHERE EXISTS (
  SELECT 1 FROM JSON_TABLE(t.attribute_schema, '$.fields[*]' COLUMNS (k VARCHAR(64) PATH '$.key')) jt
  WHERE jt.k IS NULL OR jt.k = '');
