-- 任务码：dev-20261029-005
-- 目的：订单审核附件允许“客户订单/PO”这一类；归属客户资料大类，销售/采购单据可用。
-- 影响面：新增一个附件二级类型字典项；不改既有项。

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, ext_data, sort_order, is_active)
SELECT 'attachment_document_type', 'CUSTOMER_PO', 'CUSTOMER_PO', '客户订单/PO',
       CAST('{"parent": "CUSTOMER_DOC", "sources": ["inquiry", "quotation", "sales_order", "purchase_order"]}' AS JSON),
       19, 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item WHERE dict_code = 'attachment_document_type' AND item_key = 'CUSTOMER_PO'
);

SELECT item_key, label FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='CUSTOMER_PO';
