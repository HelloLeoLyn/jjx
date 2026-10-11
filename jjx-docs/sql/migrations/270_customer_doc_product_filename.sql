-- dev-20261011-055: backfill product-scoped customer-document sequence and display name.
-- Data correction: apply only after the user's manual full backup (excluding hr_employee).
-- Existing file_name and stored file content remain unchanged.

CREATE TEMPORARY TABLE tmp_customer_doc_renumber AS
SELECT id,
       ROW_NUMBER() OVER (
         PARTITION BY biz_type, biz_id, customer_product_code, customer_doc_type
         ORDER BY create_time, id
       ) AS new_seq
  FROM sys_attachment
 WHERE customer_product_code IS NOT NULL
   AND customer_doc_type IS NOT NULL;

ALTER TABLE tmp_customer_doc_renumber ADD PRIMARY KEY (id);

UPDATE sys_attachment a
JOIN tmp_customer_doc_renumber n ON n.id = a.id
   SET a.customer_doc_seq = n.new_seq,
       a.display_name = CONCAT(
         CASE a.biz_type
           WHEN 'inquiry' THEN '询价单'
           WHEN 'quotation' THEN '报价单'
           WHEN 'sales_order' THEN '销售订单'
           WHEN 'sample' THEN '样品单'
         END, '-', a.customer_product_code, '-',
         CASE a.customer_doc_type
           WHEN 'customer_drawing' THEN '客户图纸'
           WHEN 'specification' THEN '技术规格书'
           WHEN 'approval_sheet' THEN '承认书'
           WHEN 'inspection_standard' THEN '检验标准'
           WHEN 'change_notice' THEN '变更通知'
           WHEN 'golden_sample' THEN '标准样品资料'
           WHEN 'packaging_spec' THEN '包装规范'
           WHEN 'compliance_report' THEN '认证报告'
           WHEN 'other' THEN '其他资料'
         END, '-', LPAD(n.new_seq, 2, '0'), '.',
         LOWER(SUBSTRING_INDEX(a.file_name, '.', -1))
       )
 WHERE a.biz_type IN ('inquiry', 'quotation', 'sales_order', 'sample')
   AND a.customer_doc_type IN ('customer_drawing', 'specification', 'approval_sheet',
       'inspection_standard', 'change_notice', 'golden_sample', 'packaging_spec',
       'compliance_report', 'other')
   AND a.file_name LIKE '%.%';

DROP TEMPORARY TABLE tmp_customer_doc_renumber;
