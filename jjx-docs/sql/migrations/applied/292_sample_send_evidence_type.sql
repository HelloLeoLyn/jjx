-- 292_sample_send_evidence_type.sql
-- 任务：dev-20261020-003
-- 目的：送样登记的证据附件语义修正——新增“送样凭证”二级资料类型（商务资料），作为送样登记默认；
--       客户确认/退回仍使用“客户反馈凭证”（SAMPLE_CUSTOMER_EVIDENCE），本迁移不改其定义。
-- 说明：仅字典数据，不改表结构；先更新后按 NOT EXISTS 插入，可重复执行（幂等）。
-- risk: low

UPDATE sys_dict_item
SET item_value = 'SAMPLE_SEND_EVIDENCE',
    label = '送样凭证',
    remark = '送样登记时上传的快递单、发货照片、样品照片等自有凭证',
    sort_order = 37,
    is_active = 1,
    ext_data = JSON_OBJECT('parent', 'BUSINESS_DOC', 'sources', JSON_ARRAY('sample'))
WHERE dict_code = 'attachment_document_type'
  AND item_key = 'SAMPLE_SEND_EVIDENCE'
  AND deleted = 0;

INSERT INTO sys_dict_item
  (dict_code, item_key, item_value, label, remark, sort_order, is_active, ext_data, deleted, tenant_id)
SELECT 'attachment_document_type', 'SAMPLE_SEND_EVIDENCE',
       'SAMPLE_SEND_EVIDENCE', '送样凭证',
       '送样登记时上传的快递单、发货照片、样品照片等自有凭证', 37, 1,
       JSON_OBJECT('parent', 'BUSINESS_DOC', 'sources', JSON_ARRAY('sample')), 0, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item
  WHERE dict_code = 'attachment_document_type'
    AND item_key = 'SAMPLE_SEND_EVIDENCE'
    AND deleted = 0
);
