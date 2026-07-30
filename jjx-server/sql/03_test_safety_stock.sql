-- 安全库存验证测试数据（处理完所有 FK 依赖）
SET FOREIGN_KEY_CHECKS=0;

DELETE FROM inventory_alert_log;
DELETE FROM inventory_transaction;
DELETE FROM inventory_outbound_item;
DELETE FROM inventory_outbound_order;
DELETE FROM inventory_stock_item;
DELETE FROM inventory_stock;
DELETE FROM inventory_material;
DELETE FROM inventory_material_category;

SET FOREIGN_KEY_CHECKS=1;

-- 1. 物料分类
INSERT IGNORE INTO inventory_material_category (category_id, category_name, parent_id, status)
VALUES (1, '测试分类', 0, 0);

-- 2. 物料（safe_stock=20）
INSERT INTO inventory_material (material_id, material_code, material_name, material_type, category_id, unit, safe_stock, max_stock, status, create_by, update_by)
VALUES (1, 'MAT-001', '测试物料', 'R', 1, 'PCS', 20, 100, 1, 'admin', 'admin');

-- 3. 库存（stock=5 < safe_stock=20）
INSERT INTO inventory_stock (material_id, material_code, material_name, total_quantity, total_reserved)
VALUES (1, 'MAT-001', '测试物料', 5, 0);

INSERT INTO inventory_stock_item (material_id, material_code, material_name, quantity, reserved_quantity, status, warehouse_id)
VALUES (1, 'MAT-001', '测试物料', 5, 0, 1, 1);

-- 4. 出库单（pending 状态才能确认）
INSERT INTO inventory_outbound_order (outbound_no, outbound_type, warehouse_id, outbound_date, order_status, create_by)
VALUES ('OB-TEST-008', 'manual', 1, CURDATE(), 'pending', 'admin');

SET @oid = LAST_INSERT_ID();

-- 5. 出库明细
INSERT INTO inventory_outbound_item (outbound_id, material_id, material_code, material_name, quantity, unit_price)
VALUES (@oid, 1, 'MAT-001', '测试物料', 3, 0);

-- 6. 验证
SELECT '物料创建成功' as status, material_id, material_code, safe_stock FROM inventory_material WHERE material_id=1;
SELECT '库存创建成功', material_id, total_quantity FROM inventory_stock WHERE material_id=1;
SELECT '出库单创建成功', outbound_id, outbound_no, order_status FROM inventory_outbound_order WHERE outbound_id=@oid;
SELECT '出库明细创建成功', item_id, quantity FROM inventory_outbound_item WHERE outbound_id=@oid;
