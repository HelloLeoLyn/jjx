-- 任务码：dev-20261117-010
-- 目的：采购付款「确认付款」上传转账回单时二级资料类型下拉为空（字典 sources 无 purchase_payment），
--       方案 A：新增「付款回单」二级类型，归属商务资料，来源仅采购付款。
-- 影响面：仅新增一个字典项，不改既有项；sources 不含其他来源，避免污染其它单据的可选范围。

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'PAYMENT_RECEIPT', 'PAYMENT_RECEIPT', '付款回单',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_payment"]}' AS JSON),
       38, 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item WHERE dict_code = 'attachment_document_type' AND item_key = 'PAYMENT_RECEIPT'
);

SELECT item_key, label FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PAYMENT_RECEIPT';
