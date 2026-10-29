-- 任务码：dev-20261029-006
-- 目的：统一附件二级字典 ext_data.parent 的大小写，必须命中 attachment_category.item_key。
-- 执行：bash scripts/db-migrate.sh 321_attachment_document_type_parent_case.sql --yes --task dev-20261029-006 --backup <用户手工备份>

UPDATE sys_dict_item d
JOIN sys_dict_item p
  ON p.dict_code = 'attachment_category'
 AND LOWER(p.item_key) = LOWER(JSON_UNQUOTE(JSON_EXTRACT(d.ext_data, '$.parent')))
SET d.ext_data = JSON_SET(d.ext_data, '$.parent', p.item_key)
WHERE d.dict_code = 'attachment_document_type'
  AND JSON_UNQUOTE(JSON_EXTRACT(d.ext_data, '$.parent')) IS NOT NULL
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(d.ext_data, '$.parent')) AS BINARY) <> CAST(p.item_key AS BINARY);

SELECT d.item_key,
       JSON_UNQUOTE(JSON_EXTRACT(d.ext_data, '$.parent')) AS parent
FROM sys_dict_item d
LEFT JOIN sys_dict_item p
  ON p.dict_code = 'attachment_category'
 AND LOWER(p.item_key) = LOWER(JSON_UNQUOTE(JSON_EXTRACT(d.ext_data, '$.parent')))
WHERE d.dict_code = 'attachment_document_type'
  AND JSON_UNQUOTE(JSON_EXTRACT(d.ext_data, '$.parent')) IS NOT NULL
  AND p.item_key IS NULL;
