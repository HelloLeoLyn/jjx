-- 293_attachment_work_spec_source.sql
-- 任务：dev-20261020-022
-- 仅创建，待用户授权执行；不改表结构、不改历史附件来源。
-- 保留既有 sources，只给 ENGINEERING_DOC 二级类型追加 work_spec，幂等。
-- risk: low
UPDATE sys_dict_item
SET ext_data = JSON_SET(ext_data, '$.sources',
    JSON_ARRAY_APPEND(JSON_EXTRACT(ext_data, '$.sources'), '$', 'work_spec'))
WHERE dict_code = 'attachment_document_type' AND deleted = 0
  AND JSON_UNQUOTE(JSON_EXTRACT(ext_data, '$.parent')) = 'ENGINEERING_DOC'
  AND JSON_TYPE(JSON_EXTRACT(ext_data, '$.sources')) = 'ARRAY'
  AND NOT JSON_CONTAINS(JSON_EXTRACT(ext_data, '$.sources'), JSON_QUOTE('work_spec'));
