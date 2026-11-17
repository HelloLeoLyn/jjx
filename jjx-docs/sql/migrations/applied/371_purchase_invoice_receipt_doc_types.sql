-- 任务码：dev-20261117-023
-- 目的：采购发票 / 采购收货票据的二级资料类型下拉为空（字典 sources 未登记这两个来源）。
--       方案 A（用户拍板）：不新增一级，二级统一挂「商务资料」BUSINESS_DOC，来源各自独立。
--       发票 5 个：增值税专用发票 / 增值税普通发票 / 电子发票 / 形式发票(PI) / 其他票据
--       收货票据 3 个：送货单 / 磅单 / 其他收货票据
-- 影响面：仅新增 8 个字典项，不改既有项；sources 互不交叉，避免污染其他单据的可选范围。

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'VAT_SPECIAL_INVOICE', 'VAT_SPECIAL_INVOICE', '增值税专用发票',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_invoice"]}' AS JSON), 39, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='VAT_SPECIAL_INVOICE');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'VAT_NORMAL_INVOICE', 'VAT_NORMAL_INVOICE', '增值税普通发票',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_invoice"]}' AS JSON), 40, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='VAT_NORMAL_INVOICE');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'E_INVOICE', 'E_INVOICE', '电子发票',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_invoice"]}' AS JSON), 41, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='E_INVOICE');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'PROFORMA_INVOICE', 'PROFORMA_INVOICE', '形式发票（PI）',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_invoice"]}' AS JSON), 42, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='PROFORMA_INVOICE');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'INVOICE_OTHER', 'INVOICE_OTHER', '其他票据',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_invoice"]}' AS JSON), 43, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='INVOICE_OTHER');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'DELIVERY_NOTE', 'DELIVERY_NOTE', '送货单',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_receipt"]}' AS JSON), 44, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='DELIVERY_NOTE');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'WEIGH_TICKET', 'WEIGH_TICKET', '磅单',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_receipt"]}' AS JSON), 45, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='WEIGH_TICKET');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'RECEIPT_OTHER', 'RECEIPT_OTHER', '其他收货票据',
       CAST('{"parent": "BUSINESS_DOC", "sources": ["purchase_receipt"]}' AS JSON), 46, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='RECEIPT_OTHER');

SELECT item_key, label, sort_order FROM sys_dict_item
WHERE dict_code='attachment_document_type' AND item_key IN
  ('VAT_SPECIAL_INVOICE','VAT_NORMAL_INVOICE','E_INVOICE','PROFORMA_INVOICE','INVOICE_OTHER','DELIVERY_NOTE','WEIGH_TICKET','RECEIPT_OTHER')
ORDER BY sort_order;
