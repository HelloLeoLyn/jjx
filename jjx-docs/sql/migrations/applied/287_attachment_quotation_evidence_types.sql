-- 287_attachment_quotation_evidence_types.sql
-- 任务：dev-20261018-006
-- 目的：为报价操作弹窗的“证据”增加两个专用二级资料类型，避免与普通报价附件混在一起。
-- 说明：仅新增字典项，不改表结构；可重复执行。

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'SALES_QUOTATION_REVIEW_EVIDENCE',
       'SALES_QUOTATION_REVIEW_EVIDENCE', '报价审核凭证',
       '审核通过/审核驳回时上传的证据', 33, 1,
       JSON_OBJECT('parent', 'BUSINESS_DOC', 'sources', JSON_ARRAY('quotation')), 0, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item
  WHERE dict_code = 'attachment_document_type'
    AND item_key = 'SALES_QUOTATION_REVIEW_EVIDENCE'
    AND deleted = 0
);

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'SALES_QUOTATION_CUSTOMER_EVIDENCE',
       'SALES_QUOTATION_CUSTOMER_EVIDENCE', '客户反馈凭证',
       '客户确认/客户拒绝时上传的凭证', 34, 1,
       JSON_OBJECT('parent', 'CUSTOMER_DOC', 'sources', JSON_ARRAY('quotation')), 0, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item
  WHERE dict_code = 'attachment_document_type'
    AND item_key = 'SALES_QUOTATION_CUSTOMER_EVIDENCE'
    AND deleted = 0
);
