-- ============================================================
-- Migration: V20260724_003__seed_business_data.sql
-- 填充核心业务演示数据
-- Applied: 2026-07-24
-- ============================================================

-- 产品分类
INSERT IGNORE INTO product_category (category_code, category_name, parent_id, category_level, sort_order, status) VALUES
('CAT-MEM',  '薄膜开关',    0, 1, 1, '0'),
('CAT-PCB',  'PCB板',       0, 1, 2, '0'),
('CAT-FPC',  'FPC柔性板',   0, 1, 3, '0'),
('CAT-PANEL','面板',        (SELECT category_id FROM product_category WHERE category_code='CAT-MEM'), 2, 1, '0'),
('CAT-KEY',  '按键板',      (SELECT category_id FROM product_category WHERE category_code='CAT-MEM'), 2, 2, '0');

-- 产品
INSERT IGNORE INTO product (product_code, product_name, category_id, product_type, base_price, cost_price, min_order_qty, lead_time, product_status, unit, remark) VALUES
('MS-2024-001', '医疗设备控制面板薄膜开关', (SELECT category_id FROM product_category WHERE category_code='CAT-PANEL'), 'standard', 45.00, 28.50, 100, 15, 2, 'PCS', '医疗级薄膜开关，防水防尘'),
('MS-2024-002', '工业控制器薄膜按键板',    (SELECT category_id FROM product_category WHERE category_code='CAT-KEY'),  'standard', 32.00, 19.80, 200, 12, 2, 'PCS', '工业级按键板，耐磨100万次'),
('MS-2024-003', '家电触摸控制面板',        (SELECT category_id FROM product_category WHERE category_code='CAT-PANEL'), 'standard', 28.00, 16.50, 500, 10, 2, 'PCS', '家用电器触控面板'),
('MS-2024-004', '仪器仪表薄膜开关面板',    (SELECT category_id FROM product_category WHERE category_code='CAT-PANEL'), 'standard', 38.00, 24.00, 150, 14, 2, 'PCS', '精密仪器面板'),
('MS-2024-005', '通讯设备FPC按键板',       (SELECT category_id FROM product_category WHERE category_code='CAT-KEY'),  'standard', 55.00, 35.00, 80, 18, 2, 'PCS', '通讯设备专用FPC板');

-- BOM（关联到产品product_code）
INSERT IGNORE INTO product_bom (bom_code, bom_name, product_id, bom_version, bom_type, is_current, approve_status, effective_date)
SELECT 'BOM-MS-001', '医疗面板BOM V1.0', p.product_id, 'V1.0', 'manufacturing', 1, 1, CURDATE()
FROM product p WHERE p.product_code = 'MS-2024-001';
INSERT IGNORE INTO product_bom (bom_code, bom_name, product_id, bom_version, bom_type, is_current, approve_status, effective_date)
SELECT 'BOM-MS-002', '工业按键BOM V1.0', p.product_id, 'V1.0', 'manufacturing', 1, 1, CURDATE()
FROM product p WHERE p.product_code = 'MS-2024-002';
INSERT IGNORE INTO product_bom (bom_code, bom_name, product_id, bom_version, bom_type, is_current, approve_status, effective_date)
SELECT 'BOM-MS-003', '家电面板BOM V1.0', p.product_id, 'V1.0', 'manufacturing', 1, 1, CURDATE()
FROM product p WHERE p.product_code = 'MS-2024-003';

-- BOM物料清单（关联材料）
INSERT IGNORE INTO product_bom_item (bom_id, material_id, material_code, material_name, quantity, unit, source_type)
SELECT b.bom_id, m.material_id, m.material_code, m.material_name, 1.0000, 'PCS', 'buy'
FROM product_bom b, inventory_material m
WHERE b.bom_code = 'BOM-MS-001' AND m.material_id = 1527;

INSERT IGNORE INTO product_bom_item (bom_id, material_id, material_code, material_name, quantity, unit, source_type)
SELECT b.bom_id, m.material_id, m.material_code, m.material_name, 1.0000, 'PCS', 'buy'
FROM product_bom b, inventory_material m
WHERE b.bom_code = 'BOM-MS-001' AND m.material_id = 1530;

INSERT IGNORE INTO product_bom_item (bom_id, material_id, material_code, material_name, quantity, unit, source_type)
SELECT b.bom_id, m.material_id, m.material_code, m.material_name, 1.0000, 'PCS', 'buy'
FROM product_bom b, inventory_material m
WHERE b.bom_code = 'BOM-MS-002' AND m.material_id = 1530;

INSERT IGNORE INTO product_bom_item (bom_id, material_id, material_code, material_name, quantity, unit, source_type)
SELECT b.bom_id, m.material_id, m.material_code, m.material_name, 1.0000, 'PCS', 'buy'
FROM product_bom b, inventory_material m
WHERE b.bom_code = 'BOM-MS-002' AND m.material_id = 1531;
