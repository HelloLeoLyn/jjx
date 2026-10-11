-- dev-20261011-054: preserve customer-supplied names and product assignment on ordinary business attachments.
-- Apply only through db-migrate.sh after the user's manual full backup.
ALTER TABLE sys_attachment
  ADD COLUMN customer_product_code varchar(100) DEFAULT NULL COMMENT '客供资料所属产品编码',
  ADD COLUMN customer_doc_type varchar(50) DEFAULT NULL COMMENT '客供资料类型',
  ADD COLUMN customer_doc_seq int DEFAULT NULL COMMENT '同来源单据和资料类型的序号',
  ADD COLUMN display_name varchar(255) DEFAULT NULL COMMENT '来源和资料类型组成的整理名称',
  ADD INDEX idx_customer_doc_source (biz_type, biz_id, customer_doc_type, customer_doc_seq);
