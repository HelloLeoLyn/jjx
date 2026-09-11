-- dev-20260911-009 销售订单增加客户简称快照

ALTER TABLE sales_order
  ADD COLUMN customer_short_name varchar(100) NULL COMMENT '客户简称（下单时快照）' AFTER customer_name;

UPDATE sales_order o
JOIN sales_customer c ON c.customer_id = o.customer_id
SET o.customer_short_name = c.customer_short_name
WHERE o.customer_short_name IS NULL;
