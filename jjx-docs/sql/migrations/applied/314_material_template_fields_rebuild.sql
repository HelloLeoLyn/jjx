-- 任务码：dev-20261028-002
-- 目的：彻底清掉 312 遗留的无 key 孤儿字段（厚度定义），并保证 fields 数组只含带 key 的字段。
-- 背景：312 只删掉 thickness_mm 字段对象的 key；313 的 WHERE EXISTS 判定在 MySQL 里没有命中，
--       实际只靠一次手工 UPDATE 修掉了 TPL_PET。本迁移对全部模板无条件重排 fields（幂等）。
-- 口径：保留带 key 且 key <> 'thickness_mm' 的字段，顺序按原 ORDINALITY。
-- 回滚：从 20261028-1115 全库备份恢复 inventory_attribute_template。

SET SESSION group_concat_max_len = 1000000;

UPDATE inventory_attribute_template t
SET t.attribute_schema = JSON_SET(t.attribute_schema, '$.fields', CAST(CONCAT('[', COALESCE((
      SELECT GROUP_CONCAT(JSON_UNQUOTE(jt.item) ORDER BY jt.ord SEPARATOR ',')
      FROM JSON_TABLE(t.attribute_schema, '$.fields[*]' COLUMNS (
            ord FOR ORDINALITY,
            k VARCHAR(64) PATH '$.key',
            item JSON PATH '$')) jt
      WHERE jt.k IS NOT NULL AND jt.k <> '' AND jt.k <> 'thickness_mm'
    ), ''), ']') AS JSON));

SELECT t.template_code, JSON_LENGTH(t.attribute_schema -> '$.fields') AS 字段数
FROM inventory_attribute_template t ORDER BY t.template_id;
