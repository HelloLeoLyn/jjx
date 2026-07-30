-- 测试数据种子（全模块事件测试）
-- 注意：sales_customer 已有数据（01_seed_customers.sql）

-- 1. 产品
SET @pid := (SELECT product_id FROM product WHERE product_code='P0001');
SELECT IFNULL(@pid, 0) INTO @pid;
INSERT INTO product (product_code, product_name, category_id, product_status, create_by, update_by, unit)
SELECT 'P0001', '测试薄膜开关', 1, 3, 'admin', 'admin', '个'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE product_code='P0001');
SELECT IF(@@row_count>0, @pid := LAST_INSERT_ID(), @pid := (SELECT product_id FROM product WHERE product_code='P0001'));

-- 2. BOM（草稿状态才能提审）
INSERT INTO product_bom (bom_code, bom_name, product_id, approve_status, create_by, create_time)
VALUES ('BOM0001', '测试BOM', @pid, 1, 'admin', NOW());

-- 3. 采购单（草稿状态）
INSERT INTO purchase_order (order_no, supplier_id, supplier_name, order_date, expected_delivery_date, approval_status, create_by, create_time)
VALUES ('PO20260730001', 1, '测试供应商', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 1, 'admin', NOW());

-- 4. 报价单
INSERT INTO sales_quotation (quotation_no, customer_id, customer_name, quotation_date, valid_until, status, create_by, create_time)
VALUES ('QO20260730001', 1, '测试客户', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 0, 'admin', NOW());

-- 5. 销售订单
INSERT INTO sales_order (order_no, customer_id, customer_name, order_date, order_status, create_by, create_time)
VALUES ('SO20260730001', 1, '测试客户', CURDATE(), 0, 'admin', NOW());

-- 6. 生产工单
INSERT INTO production_order (order_no, order_type, product_id, product_code, product_name, order_status, quantity, create_by, create_time)
VALUES ('MO20260730001', 'manufacture', @pid, 'P0001', '测试薄膜开关', 0, 100, 'admin', NOW());
