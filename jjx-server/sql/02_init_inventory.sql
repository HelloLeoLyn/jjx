-- ============================================================
-- 库存模块初始化数据
-- 执行顺序：第2个执行（依赖系统模块）
-- 包含：仓库、库位、物料分类、物料
-- ============================================================

-- ==================== 1. 仓库数据 ====================
INSERT IGNORE INTO inventory_warehouse (warehouse_id, warehouse_code, warehouse_name, warehouse_type, location, manager, contact_phone, sort_order, status, create_by, create_time, update_by, update_time) VALUES
(1, 'WH-RAW', '原材料仓', 'normal', 'A栋1楼', '孙七', '13800138006', 1, '0', 'system', NOW(), 'system', NOW()),
(2, 'WH-SEMI', '半成品仓', 'normal', 'A栋2楼', '孙七', '13800138006', 2, '0', 'system', NOW(), 'system', NOW()),
(3, 'WH-FIN', '成品仓', 'finished', 'A栋3楼', '孙七', '13800138006', 3, '0', 'system', NOW(), 'system', NOW()),
(4, 'WH-QC', '质检仓', 'quality', 'B栋1楼', '赵六', '13800138005', 4, '0', 'system', NOW(), 'system', NOW()),
(5, 'WH-SCRAP', '废品仓', 'scrap', 'B栋2楼', '赵六', '13800138005', 5, '0', 'system', NOW(), 'system', NOW());

-- ==================== 2. 库位数据 ====================
INSERT IGNORE INTO inventory_storage_location (location_id, warehouse_id, location_code, location_name, location_type, area_code, aisle, shelf, layer, max_capacity, current_usage, status, create_by, create_time, update_by, update_time, remark) VALUES
-- 原材料仓库位
(1, 1, 'RAW-A-01-01', 'A区01架01层', 'shelf', 'A', '01', '01', '01', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(2, 1, 'RAW-A-01-02', 'A区01架02层', 'shelf', 'A', '01', '01', '02', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(3, 1, 'RAW-A-01-03', 'A区01架03层', 'shelf', 'A', '01', '01', '03', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(4, 1, 'RAW-A-02-01', 'A区02架01层', 'shelf', 'A', '01', '02', '01', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(5, 1, 'RAW-A-02-02', 'A区02架02层', 'shelf', 'A', '01', '02', '02', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(6, 1, 'RAW-B-01-01', 'B区01架01层', 'shelf', 'B', '02', '01', '01', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(7, 1, 'RAW-B-01-02', 'B区01架02层', 'shelf', 'B', '02', '01', '02', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 半成品仓库位
(8, 2, 'SEMI-A-01-01', 'A区01架01层', 'shelf', 'A', '01', '01', '01', 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(9, 2, 'SEMI-A-01-02', 'A区01架02层', 'shelf', 'A', '01', '01', '02', 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(10, 2, 'SEMI-B-01-01', 'B区01架01层', 'shelf', 'B', '02', '01', '01', 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 成品仓库位
(11, 3, 'FIN-A-01-01', 'A区01架01层', 'shelf', 'A', '01', '01', '01', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(12, 3, 'FIN-A-01-02', 'A区01架02层', 'shelf', 'A', '01', '01', '02', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(13, 3, 'FIN-A-02-01', 'A区02架01层', 'shelf', 'A', '01', '02', '01', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(14, 3, 'FIN-B-01-01', 'B区01架01层', 'shelf', 'B', '02', '01', '01', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 质检仓库位
(15, 4, 'QC-A-01-01', '待检区01', 'area', 'A', '01', NULL, NULL, 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), '待检验物料暂存区'),
(16, 4, 'QC-A-01-02', '不合格区01', 'area', 'A', '01', NULL, NULL, 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), '不合格品暂存区'),
-- 废品仓库位
(17, 5, 'SCRAP-A-01-01', '废品区01', 'area', 'A', '01', NULL, NULL, 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL);

-- ==================== 3. 物料分类数据 ====================
INSERT IGNORE INTO inventory_material_category (category_id, category_code, category_name, parent_id, category_level, category_path, sort_order, status, create_by, create_time, update_by, update_time) VALUES
-- 一级分类
(1, 'BASE', '基材类', 0, 1, '/1', 1, '0', 'system', NOW(), 'system', NOW()),
(2, 'INK', '油墨类', 0, 1, '/2', 2, '0', 'system', NOW(), 'system', NOW()),
(3, 'ADH', '胶粘类', 0, 1, '/3', 3, '0', 'system', NOW(), 'system', NOW()),
(4, 'ELEC', '电子元件类', 0, 1, '/4', 4, '0', 'system', NOW(), 'system', NOW()),
(5, 'PACK', '包装材料类', 0, 1, '/5', 5, '0', 'system', NOW(), 'system', NOW()),
(6, 'AUX', '辅助材料类', 0, 1, '/6', 6, '0', 'system', NOW(), 'system', NOW()),
-- 二级分类 - 基材类
(11, 'PET', 'PET基材', 1, 2, '/1/11', 1, '0', 'system', NOW(), 'system', NOW()),
(12, 'PC', 'PC基材', 1, 2, '/1/12', 2, '0', 'system', NOW(), 'system', NOW()),
(13, 'PVC', 'PVC基材', 1, 2, '/1/13', 3, '0', 'system', NOW(), 'system', NOW()),
-- 二级分类 - 油墨类
(21, 'SILVER', '导电银浆', 2, 2, '/2/21', 1, '0', 'system', NOW(), 'system', NOW()),
(22, 'INSULATE', '绝缘油墨', 2, 2, '/2/22', 2, '0', 'system', NOW(), 'system', NOW()),
(23, 'CARBON', '碳浆', 2, 2, '/2/23', 3, '0', 'system', NOW(), 'system', NOW()),
(24, 'UV', 'UV油墨', 2, 2, '/2/24', 4, '0', 'system', NOW(), 'system', NOW()),
(25, 'COLOR', '彩色油墨', 2, 2, '/2/25', 5, '0', 'system', NOW(), 'system', NOW()),
-- 二级分类 - 胶粘类
(31, '3M', '3M胶带', 3, 2, '/3/31', 1, '0', 'system', NOW(), 'system', NOW()),
(32, 'TESA', 'TESA胶带', 3, 2, '/3/32', 2, '0', 'system', NOW(), 'system', NOW()),
(33, 'NITTO', '日东胶带', 3, 2, '/3/33', 3, '0', 'system', NOW(), 'system', NOW()),
-- 二级分类 - 电子元件类
(41, 'LED', 'LED灯珠', 4, 2, '/4/41', 1, '0', 'system', NOW(), 'system', NOW()),
(42, 'CONN', '连接器', 4, 2, '/4/42', 2, '0', 'system', NOW(), 'system', NOW()),
(43, 'RES', '电阻', 4, 2, '/4/43', 3, '0', 'system', NOW(), 'system', NOW()),
-- 二级分类 - 包装材料类
(51, 'BAG', '包装袋', 5, 2, '/5/51', 1, '0', 'system', NOW(), 'system', NOW()),
(52, 'BOX', '包装盒/箱', 5, 2, '/5/52', 2, '0', 'system', NOW(), 'system', NOW()),
(53, 'FOAM', '缓冲材料', 5, 2, '/5/53', 3, '0', 'system', NOW(), 'system', NOW()),
-- 二级分类 - 辅助材料类
(61, 'SCREEN', '网版', 6, 2, '/6/61', 1, '0', 'system', NOW(), 'system', NOW()),
(62, 'FILM', '菲林', 6, 2, '/6/62', 2, '0', 'system', NOW(), 'system', NOW()),
(63, 'TOOL', '模具/刀具', 6, 2, '/6/63', 3, '0', 'system', NOW(), 'system', NOW()),
(64, 'CHEM', '化学品', 6, 2, '/6/64', 4, '0', 'system', NOW(), 'system', NOW());

-- ==================== 4. 物料数据 ====================
INSERT IGNORE INTO inventory_material (material_id, material_code, material_name, material_name_en, material_type, category_id, specification, unit, unit_conv, unit_alt, batch_control, shelf_life, expiry_alert_days, safe_stock, max_stock, reorder_point, standard_price, lead_time, supplier_id, supplier_name, default_warehouse_id, default_location_id, status, process_group, create_by, create_time, update_by, update_time) VALUES
-- PET基材
(1, 'PET-0125', 'PET基材0.125mm', 'PET Film 0.125mm', 'R', 11, '0.125mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 100.0000, 1000.0000, 200.0000, 25.0000, 7, NULL, NULL, 1, 1, 0, NULL, 'system', NOW(), 'system', NOW()),
(2, 'PET-0188', 'PET基材0.188mm', 'PET Film 0.188mm', 'R', 11, '0.188mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 100.0000, 1000.0000, 200.0000, 30.0000, 7, NULL, NULL, 1, 1, 0, NULL, 'system', NOW(), 'system', NOW()),
(3, 'PET-0250', 'PET基材0.25mm', 'PET Film 0.25mm', 'R', 11, '0.25mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 80.0000, 800.0000, 150.0000, 35.0000, 7, NULL, NULL, 1, 1, 0, NULL, 'system', NOW(), 'system', NOW()),
-- PC基材
(4, 'PC-0125', 'PC基材0.125mm', 'PC Film 0.125mm', 'R', 12, '0.125mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 50.0000, 500.0000, 100.0000, 45.0000, 10, NULL, NULL, 1, 2, 0, NULL, 'system', NOW(), 'system', NOW()),
(5, 'PC-0250', 'PC基材0.25mm', 'PC Film 0.25mm', 'R', 12, '0.25mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 50.0000, 500.0000, 100.0000, 55.0000, 10, NULL, NULL, 1, 2, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 导电银浆
(6, 'SILVER-A', '导电银浆A型', 'Silver Conductive Paste Type A', 'R', 21, '1kg/瓶', 'kg', 1.0000, NULL, 1, 180, 15, 10.0000, 100.0000, 20.0000, 850.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(7, 'SILVER-B', '导电银浆B型', 'Silver Conductive Paste Type B', 'R', 21, '1kg/瓶', 'kg', 1.0000, NULL, 1, 180, 15, 5.0000, 50.0000, 10.0000, 920.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 绝缘油墨
(8, 'INS-WHITE', '白色绝缘油墨', 'White Insulation Ink', 'R', 22, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 5.0000, 50.0000, 10.0000, 120.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(9, 'INS-YELLOW', '黄色绝缘油墨', 'Yellow Insulation Ink', 'R', 22, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 3.0000, 30.0000, 5.0000, 130.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 碳浆
(10, 'CARBON-BLK', '黑色碳浆', 'Black Carbon Paste', 'R', 23, '1kg/瓶', 'kg', 1.0000, NULL, 1, 180, 15, 3.0000, 30.0000, 5.0000, 280.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
-- UV油墨
(11, 'UV-CLEAR', 'UV光油', 'UV Varnish', 'R', 24, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 2.0000, 20.0000, 5.0000, 95.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 彩色油墨
(12, 'INK-BLACK', '黑色油墨', 'Black Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 2.0000, 20.0000, 5.0000, 65.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(13, 'INK-WHITE', '白色油墨', 'White Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 2.0000, 20.0000, 5.0000, 60.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(14, 'INK-RED', '红色油墨', 'Red Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 1.0000, 10.0000, 2.0000, 70.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(15, 'INK-BLUE', '蓝色油墨', 'Blue Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 1.0000, 10.0000, 2.0000, 70.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 3M胶带
(16, '3M-467', '3M 467胶带', '3M 467 Adhesive', 'R', 31, '200mm×50m', 'm²', 1.0000, NULL, 1, 730, 60, 20.0000, 200.0000, 40.0000, 180.0000, 7, NULL, NULL, 1, 4, 0, NULL, 'system', NOW(), 'system', NOW()),
(17, '3M-468', '3M 468胶带', '3M 468 Adhesive', 'R', 31, '200mm×50m', 'm²', 1.0000, NULL, 1, 730, 60, 20.0000, 200.0000, 40.0000, 220.0000, 7, NULL, NULL, 1, 4, 0, NULL, 'system', NOW(), 'system', NOW()),
-- TESA胶带
(18, 'TESA-4965', 'TESA 4965胶带', 'TESA 4965 Adhesive', 'R', 32, '200mm×50m', 'm²', 1.0000, NULL, 1, 730, 60, 10.0000, 100.0000, 20.0000, 195.0000, 10, NULL, NULL, 1, 4, 0, NULL, 'system', NOW(), 'system', NOW()),
-- LED灯珠
(19, 'LED-0603', '0603 LED灯珠', '0603 LED', 'R', 41, '0603/蓝色', 'pcs', 1000.0000, 'Kpcs', 1, 1095, 90, 5000.0000, 50000.0000, 10000.0000, 0.0800, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
(20, 'LED-0805', '0805 LED灯珠', '0805 LED', 'R', 41, '0805/绿色', 'pcs', 1000.0000, 'Kpcs', 1, 1095, 90, 3000.0000, 30000.0000, 5000.0000, 0.1200, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 连接器
(21, 'CONN-ZIF', 'ZIF连接器', 'ZIF Connector', 'R', 42, '8pin/0.5mm间距', 'pcs', 100.0000, NULL, 0, NULL, NULL, 1000.0000, 10000.0000, 2000.0000, 0.5000, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
(22, 'CONN-FPC', 'FPC连接器', 'FPC Connector', 'R', 42, '12pin/0.5mm间距', 'pcs', 100.0000, NULL, 0, NULL, NULL, 1000.0000, 10000.0000, 2000.0000, 0.6500, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 包装材料
(23, 'BAG-ANTI', '防静电包装袋', 'Anti-static Bag', 'R', 51, '300×400mm', 'pcs', 100.0000, NULL, 0, NULL, NULL, 500.0000, 5000.0000, 1000.0000, 0.3500, 7, NULL, NULL, 1, 6, 0, NULL, 'system', NOW(), 'system', NOW()),
(24, 'BOX-CORR', '瓦楞纸箱', 'Corrugated Box', 'R', 52, '400×300×200mm', 'pcs', 1.0000, NULL, 0, NULL, NULL, 50.0000, 500.0000, 100.0000, 3.5000, 5, NULL, NULL, 1, 6, 0, NULL, 'system', NOW(), 'system', NOW()),
(25, 'FOAM-PE', 'PE珍珠棉', 'PE Foam', 'R', 53, '5mm×1m×100m', 'm²', 1.0000, NULL, 0, NULL, NULL, 20.0000, 200.0000, 40.0000, 8.0000, 5, NULL, NULL, 1, 6, 0, NULL, 'system', NOW(), 'system', NOW()),
-- 辅助材料
(26, 'SCREEN-250', '250目网版', '250 Mesh Screen', 'A', 61, '250目/500×500mm', 'pcs', 1.0000, NULL, 0, NULL, NULL, 5.0000, 50.0000, 10.0000, 85.0000, 7, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(27, 'SCREEN-300', '300目网版', '300 Mesh Screen', 'A', 61, '300目/500×500mm', 'pcs', 1.0000, NULL, 0, NULL, NULL, 5.0000, 50.0000, 10.0000, 95.0000, 7, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(28, 'FILM-POS', '阳片菲林', 'Positive Film', 'A', 62, 'A4尺寸', 'pcs', 1.0000, NULL, 0, NULL, NULL, 10.0000, 100.0000, 20.0000, 25.0000, 3, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(29, 'CHEM-ALC', '无水酒精', 'Anhydrous Alcohol', 'A', 64, '500ml/瓶', '瓶', 1.0000, NULL, 1, 730, 60, 10.0000, 100.0000, 20.0000, 15.0000, 3, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(30, 'CHEM-THIN', '稀释剂', 'Thinner', 'A', 64, '1L/瓶', '瓶', 1.0000, NULL, 1, 730, 60, 5.0000, 50.0000, 10.0000, 28.0000, 3, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW());

-- ============================================================
-- 数据验证
-- ============================================================
-- SELECT 'inventory_warehouse' AS table_name, COUNT(*) AS count FROM inventory_warehouse
-- UNION ALL SELECT 'inventory_storage_location', COUNT(*) FROM inventory_storage_location
-- UNION ALL SELECT 'inventory_material_category', COUNT(*) FROM inventory_material_category
-- UNION ALL SELECT 'inventory_material', COUNT(*) FROM inventory_material;
