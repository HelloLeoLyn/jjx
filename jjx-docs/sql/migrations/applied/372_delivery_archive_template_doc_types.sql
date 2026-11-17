-- 任务码：dev-20261117-024
-- 目的：承接 371，补齐另外三个凭证类来源的二级资料类型（否则入口下拉仍为空）：
--       ① 质量档案回传 quality_archive：不造新概念，直接让既有 QUALITY_DOC 四个类型也可用于回传件；
--       ② 销售送货回签 sales_delivery：新增 3 个商务资料类型；
--       ③ 质量模板 quality_template：新增 2 个质量资料类型。
-- 影响面：① 仅给 4 条既有字典项的 sources 追加 quality_archive（幂等，不重复追加）；
--         ②③ 仅新增 5 条字典项，不改既有项。

-- ① 既有质量类型追加 quality_archive 来源
UPDATE sys_dict_item
   SET ext_data = JSON_SET(ext_data, '$.sources',
                   JSON_ARRAY_APPEND(JSON_EXTRACT(ext_data, '$.sources'), '$', 'quality_archive'))
 WHERE dict_code = 'attachment_document_type'
   AND item_key IN ('INSPECTION_REPORT','TEST_RECORD','CERTIFICATE_OF_CONFORMITY','ABNORMAL_REPORT')
   AND ext_data IS NOT NULL
   AND NOT JSON_CONTAINS(JSON_EXTRACT(ext_data, '$.sources'), '"quality_archive"');

-- ② 销售送货回签（回签件=结算依据，挂商务资料）
INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'DELIVERY_SIGNED', 'DELIVERY_SIGNED', '客户签收送货单',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["sales_delivery"]}' AS JSON), 47, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DELIVERY_SIGNED');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'LOGISTICS_RECEIPT', 'LOGISTICS_RECEIPT', '物流签收单',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["sales_delivery"]}' AS JSON), 48, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='LOGISTICS_RECEIPT');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'DELIVERY_SIGN_OTHER', 'DELIVERY_SIGN_OTHER', '其他回签件',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["sales_delivery"]}' AS JSON), 49, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DELIVERY_SIGN_OTHER');

-- ③ 质量模板（模板文件本身 + 外来检验标准）
INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'QUALITY_TEMPLATE_FILE', 'QUALITY_TEMPLATE_FILE', '质量模板文件',
       CAST('{"parent": "QUALITY_DOC", "sources": ["quality_template"]}' AS JSON), 50, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='QUALITY_TEMPLATE_FILE');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'QUALITY_STANDARD_FILE', 'QUALITY_STANDARD_FILE', '检验标准/规范',
       CAST('{"parent": "QUALITY_DOC", "sources": ["quality_template"]}' AS JSON), 51, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='QUALITY_STANDARD_FILE');

-- 回查：三个来源各自可用类型数 + 新增项明细
SELECT 'sales_delivery' AS source_type, COUNT(*) AS doc_types FROM sys_dict_item
 WHERE dict_code='attachment_document_type' AND deleted=0 AND JSON_CONTAINS(ext_data->'$.sources','"sales_delivery"')
UNION ALL SELECT 'quality_archive', COUNT(*) FROM sys_dict_item
 WHERE dict_code='attachment_document_type' AND deleted=0 AND JSON_CONTAINS(ext_data->'$.sources','"quality_archive"')
UNION ALL SELECT 'quality_template', COUNT(*) FROM sys_dict_item
 WHERE dict_code='attachment_document_type' AND deleted=0 AND JSON_CONTAINS(ext_data->'$.sources','"quality_template"');

SELECT item_key, label, sort_order FROM sys_dict_item
 WHERE dict_code='attachment_document_type'
   AND item_key IN ('DELIVERY_SIGNED','LOGISTICS_RECEIPT','DELIVERY_SIGN_OTHER','QUALITY_TEMPLATE_FILE','QUALITY_STANDARD_FILE')
 ORDER BY sort_order;
