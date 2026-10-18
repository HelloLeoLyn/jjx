-- 288_attachment_sample_evidence_types.sql
-- 任务：dev-20261018-020
-- 目的：样品单操作弹窗的证据区增加两个专用二级资料类型，避免上传证据落库为“未分类”。
-- 说明：仅新增字典项，不改表结构；NOT EXISTS 幂等，可重复执行。
-- risk: low

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'SAMPLE_REVIEW_EVIDENCE',
       'SAMPLE_REVIEW_EVIDENCE', '样品审核凭证',
       '样品单审核通过/审核驳回时上传的证据', 35, 1,
       JSON_OBJECT('parent', 'BUSINESS_DOC', 'sources', JSON_ARRAY('sample')), 0, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item
  WHERE dict_code = 'attachment_document_type'
    AND item_key = 'SAMPLE_REVIEW_EVIDENCE'
    AND deleted = 0
);

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'SAMPLE_CUSTOMER_EVIDENCE',
       'SAMPLE_CUSTOMER_EVIDENCE', '客户反馈凭证',
       '样品单送样/客户确认/退回时上传的凭证', 36, 1,
       JSON_OBJECT('parent', 'CUSTOMER_DOC', 'sources', JSON_ARRAY('sample')), 0, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item
  WHERE dict_code = 'attachment_document_type'
    AND item_key = 'SAMPLE_CUSTOMER_EVIDENCE'
    AND deleted = 0
);
