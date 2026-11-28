-- dev-20261128-003：菲林版本上传附件使用print_artwork_version来源。
-- 沿用既有「工程技术资料 / 菲林」两级字典，只补充本来源，不改其它资料类型或既有来源。
-- 幂等配置补齐；不创建新类型，不改业务附件或图纸数据。
START TRANSACTION;
UPDATE sys_dict_item
SET ext_data = JSON_ARRAY_APPEND(ext_data, '$.sources', 'print_artwork_version'),
    update_time = NOW()
WHERE dict_code = 'attachment_document_type'
  AND item_key = 'FILM'
  AND deleted = 0
  AND is_active = 1
  AND JSON_EXTRACT(ext_data, '$.parent') = 'ENGINEERING_DOC'
  AND JSON_TYPE(JSON_EXTRACT(ext_data, '$.sources')) = 'ARRAY'
  AND NOT JSON_CONTAINS(JSON_EXTRACT(ext_data, '$.sources'), JSON_QUOTE('print_artwork_version'));
COMMIT;
SELECT dict_code, item_key, label, ext_data FROM sys_dict_item
WHERE dict_code = 'attachment_document_type' AND item_key = 'FILM' AND deleted = 0;
