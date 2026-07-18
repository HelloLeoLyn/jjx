-- ============================================================
-- 产品模块初始化数据
-- 执行顺序：第3个执行（依赖系统模块、库存模块）
-- 包含：产品分类、产品、BOM、BOM明细、工艺路线、工艺路线明细
-- 注意：标准工序数据在 product_standard_process_init.sql 中
-- ============================================================

-- ==================== 1. 产品分类数据 ====================
INSERT IGNORE INTO product_category (category_id, category_code, category_name, parent_id, category_level, sort_order, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, 'FS', '薄膜开关', 0, 1, 1, '0', 'system', NOW(), 'system', NOW(), '薄膜开关类产品'),
(2, 'NP', '铭板', 0, 1, 2, '0', 'system', NOW(), 'system', NOW(), '铭板类产品'),
(3, 'PL', '面板', 0, 1, 3, '0', 'system', NOW(), 'system', NOW(), '面板类产品'),
(4, 'LB', '标签', 0, 1, 4, '0', 'system', NOW(), 'system', NOW(), '标签类产品'),
-- 薄膜开关子分类
(11, 'FS-MEM', '薄膜开关-按键型', 1, 2, 1, '0', 'system', NOW(), 'system', NOW(), '按键式薄膜开关'),
(12, 'FS-TOUCH', '薄膜开关-触摸型', 1, 2, 2, '0', 'system', NOW(), 'system', NOW(), '触摸式薄膜开关'),
(13, 'FS-BACK', '薄膜开关-背光型', 1, 2, 3, '0', 'system', NOW(), 'system', NOW(), '带背光薄膜开关'),
-- 铭板子分类
(21, 'NP-MET', '金属铭板', 2, 2, 1, '0', 'system', NOW(), 'system', NOW(), '金属材质铭板'),
(22, 'NP-PLASTIC', '塑料铭板', 2, 2, 2, '0', 'system', NOW(), 'system', NOW(), '塑料材质铭板');

-- ==================== 2. 产品数据 ====================
INSERT IGNORE INTO product (product_id, product_code, product_name, category_id, product_type, spec_json, base_price, cost_price, min_order_qty, lead_time, product_status, current_bom_id, current_route_id, create_by, create_time, update_by, update_time, remark, unit) VALUES
(1, 'FS-2024-001', '6键薄膜开关面板', 11, 'standard',
 '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6,"keyType":"snap","circuitType":"single","connector":"ZIF-8pin","voltage":"12V","current":"100mA","lifeCycle":"100万次","operatingForce":"1.5-3.0N","operatingTemp":"-20~70℃"}',
 15.0000, 8.5000, 100, 15, 1, NULL, NULL, 'system', NOW(), 'system', NOW(), '标准6键薄膜开关面板，适用于工业控制设备', 'pcs'),
(2, 'FS-2024-002', '12键背光薄膜开关', 13, 'standard',
 '{"dimensions":{"width":120,"height":160,"unit":"mm"},"keyCount":12,"keyType":"snap","circuitType":"double","connector":"FPC-12pin","voltage":"12V","current":"200mA","lifeCycle":"100万次","operatingForce":"1.5-3.0N","operatingTemp":"-20~70℃","backlight":"LED-blue"}',
 28.0000, 16.5000, 50, 20, 1, NULL, NULL, 'system', NOW(), 'system', NOW(), '12键带蓝色背光薄膜开关，适用于医疗设备', 'pcs'),
(3, 'FS-2024-003', '4键触摸薄膜开关', 12, 'standard',
 '{"dimensions":{"width":60,"height":100,"unit":"mm"},"keyCount":4,"keyType":"touch","circuitType":"single","connector":"ZIF-6pin","voltage":"5V","current":"50mA","lifeCycle":"500万次","operatingTemp":"-20~70℃","touchSensitivity":"可调"}',
 22.0000, 12.0000, 100, 20, 0, NULL, NULL, 'system', NOW(), 'system', NOW(), '4键触摸式薄膜开关，适用于智能家居设备', 'pcs');

-- ==================== 3. BOM数据 ====================
-- 产品1的BOM
INSERT IGNORE INTO product_bom (bom_id, bom_code, bom_name, bom_type, bom_version, product_id, approve_status, approve_remark, is_current, create_by, create_time, update_by, update_time, remark, effective_date, expiry_date) VALUES
(1, 'BOM-FS001-V1', '6键薄膜开关BOM V1.0', 'manufacturing', 'V1.0', 1, 1, NULL, 1, 'system', NOW(), 'system', NOW(), '6键薄膜开关标准BOM', '2024-01-01 00:00:00', '2025-12-31 00:00:00'),
(2, 'BOM-FS002-V1', '12键背光薄膜开关BOM V1.0', 'manufacturing', 'V1.0', 2, 1, NULL, 1, 'system', NOW(), 'system', NOW(), '12键背光薄膜开关标准BOM', '2024-01-01 00:00:00', '2025-12-31 00:00:00'),
(3, 'BOM-FS003-V1', '4键触摸薄膜开关BOM V1.0', 'manufacturing', 'V1.0', 3, 0, NULL, 1, 'system', NOW(), 'system', NOW(), '4键触摸薄膜开关标准BOM', '2024-01-01 00:00:00', '2025-12-31 00:00:00');

-- 更新产品的当前BOM ID
UPDATE product SET current_bom_id = 1 WHERE product_id = 1;
UPDATE product SET current_bom_id = 2 WHERE product_id = 2;
UPDATE product SET current_bom_id = 3 WHERE product_id = 3;

-- ==================== 4. BOM明细数据 ====================
-- 产品1 BOM明细（6键薄膜开关）
INSERT IGNORE INTO product_bom_item (item_id, bom_id, material_id, material_code, material_name, specification, unit, quantity, loss_rate, module_qty, base_qty, min_issue_qty, width_mm, length_mm, layer, position_no, source_type, substitute_json, item_order, remark, create_by, create_time, update_by, update_time) VALUES
(1, 1, 1, 'PET-0125', 'PET基材0.125mm', '0.125mm×500mm×200m', 'm²', 0.0100, 5, NULL, NULL, NULL, 80, 120, 'overlay', NULL, 'buy', NULL, 1, '面板层材料', 'system', NOW(), 'system', NOW()),
(2, 1, 1, 'PET-0125', 'PET基材0.125mm', '0.125mm×500mm×200m', 'm²', 0.0100, 5, NULL, NULL, NULL, 80, 120, 'upper_circuit', NULL, 'buy', NULL, 2, '上层线路材料', 'system', NOW(), 'system', NOW()),
(3, 1, 4, 'PC-0125', 'PC基材0.125mm', '0.125mm×500mm×200m', 'm²', 0.0100, 5, NULL, NULL, NULL, 80, 120, 'spacer', NULL, 'buy', NULL, 3, '间隔层材料', 'system', NOW(), 'system', NOW()),
(4, 1, 1, 'PET-0125', 'PET基材0.125mm', '0.125mm×500mm×200m', 'm²', 0.0100, 5, NULL, NULL, NULL, 80, 120, 'lower_circuit', NULL, 'buy', NULL, 4, '下层线路材料', 'system', NOW(), 'system', NOW()),
(5, 1, 16, '3M-467', '3M 467胶带', '200mm×50m', 'm²', 0.0100, 3, NULL, NULL, NULL, 80, 120, 'back_adhesive', NULL, 'buy', NULL, 5, '背胶材料', 'system', NOW(), 'system', NOW()),
(6, 1, 6, 'SILVER-A', '导电银浆A型', '1kg/瓶', 'kg', 0.0020, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 6, '线路印刷用银浆', 'system', NOW(), 'system', NOW()),
(7, 1, 8, 'INS-WHITE', '白色绝缘油墨', '1kg/罐', 'kg', 0.0015, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 7, '绝缘层油墨', 'system', NOW(), 'system', NOW()),
(8, 1, 12, 'INK-BLACK', '黑色油墨', '1kg/罐', 'kg', 0.0010, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 8, '面板文字印刷', 'system', NOW(), 'system', NOW()),
(9, 1, 21, 'CONN-ZIF', 'ZIF连接器', '8pin/0.5mm间距', 'pcs', 1.0000, 0, NULL, NULL, NULL, NULL, NULL, NULL, 'J1', 'buy', NULL, 9, '输出连接器', 'system', NOW(), 'system', NOW());

-- 产品2 BOM明细（12键背光薄膜开关）
INSERT IGNORE INTO product_bom_item (item_id, bom_id, material_id, material_code, material_name, specification, unit, quantity, loss_rate, module_qty, base_qty, min_issue_qty, width_mm, length_mm, layer, position_no, source_type, substitute_json, item_order, remark, create_by, create_time, update_by, update_time) VALUES
(10, 2, 2, 'PET-0188', 'PET基材0.188mm', '0.188mm×500mm×200m', 'm²', 0.0200, 5, NULL, NULL, NULL, 120, 160, 'overlay', NULL, 'buy', NULL, 1, '面板层材料', 'system', NOW(), 'system', NOW()),
(11, 2, 2, 'PET-0188', 'PET基材0.188mm', '0.188mm×500mm×200m', 'm²', 0.0200, 5, NULL, NULL, NULL, 120, 160, 'upper_circuit', NULL, 'buy', NULL, 2, '上层线路材料', 'system', NOW(), 'system', NOW()),
(12, 2, 4, 'PC-0125', 'PC基材0.125mm', '0.125mm×500mm×200m', 'm²', 0.0200, 5, NULL, NULL, NULL, 120, 160, 'spacer', NULL, 'buy', NULL, 3, '间隔层材料', 'system', NOW(), 'system', NOW()),
(13, 2, 2, 'PET-0188', 'PET基材0.188mm', '0.188mm×500mm×200m', 'm²', 0.0200, 5, NULL, NULL, NULL, 120, 160, 'lower_circuit', NULL, 'buy', NULL, 4, '下层线路材料', 'system', NOW(), 'system', NOW()),
(14, 2, 17, '3M-468', '3M 468胶带', '200mm×50m', 'm²', 0.0200, 3, NULL, NULL, NULL, 120, 160, 'back_adhesive', NULL, 'buy', NULL, 5, '背胶材料', 'system', NOW(), 'system', NOW()),
(15, 2, 7, 'SILVER-B', '导电银浆B型', '1kg/瓶', 'kg', 0.0030, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 6, '线路印刷用银浆', 'system', NOW(), 'system', NOW()),
(16, 2, 8, 'INS-WHITE', '白色绝缘油墨', '1kg/罐', 'kg', 0.0020, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 7, '绝缘层油墨', 'system', NOW(), 'system', NOW()),
(17, 2, 12, 'INK-BLACK', '黑色油墨', '1kg/罐', 'kg', 0.0015, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 8, '面板文字印刷', 'system', NOW(), 'system', NOW()),
(18, 2, 19, 'LED-0603', '0603 LED灯珠', '0603/蓝色', 'pcs', 4.0000, 2, NULL, NULL, NULL, NULL, NULL, NULL, 'LED1-LED4', 'buy', NULL, 9, '背光LED灯珠', 'system', NOW(), 'system', NOW()),
(19, 2, 22, 'CONN-FPC', 'FPC连接器', '12pin/0.5mm间距', 'pcs', 1.0000, 0, NULL, NULL, NULL, NULL, NULL, NULL, 'J1', 'buy', NULL, 10, '输出连接器', 'system', NOW(), 'system', NOW());

-- 产品3 BOM明细（4键触摸薄膜开关）
INSERT IGNORE INTO product_bom_item (item_id, bom_id, material_id, material_code, material_name, specification, unit, quantity, loss_rate, module_qty, base_qty, min_issue_qty, width_mm, length_mm, layer, position_no, source_type, substitute_json, item_order, remark, create_by, create_time, update_by, update_time) VALUES
(20, 3, 3, 'PET-0250', 'PET基材0.25mm', '0.25mm×500mm×200m', 'm²', 0.0060, 5, NULL, NULL, NULL, 60, 100, 'overlay', NULL, 'buy', NULL, 1, '面板层材料', 'system', NOW(), 'system', NOW()),
(21, 3, 3, 'PET-0250', 'PET基材0.25mm', '0.25mm×500mm×200m', 'm²', 0.0060, 5, NULL, NULL, NULL, 60, 100, 'upper_circuit', NULL, 'buy', NULL, 2, '上层线路材料', 'system', NOW(), 'system', NOW()),
(22, 3, 4, 'PC-0125', 'PC基材0.125mm', '0.125mm×500mm×200m', 'm²', 0.0060, 5, NULL, NULL, NULL, 60, 100, 'spacer', NULL, 'buy', NULL, 3, '间隔层材料', 'system', NOW(), 'system', NOW()),
(23, 3, 3, 'PET-0250', 'PET基材0.25mm', '0.25mm×500mm×200m', 'm²', 0.0060, 5, NULL, NULL, NULL, 60, 100, 'lower_circuit', NULL, 'buy', NULL, 4, '下层线路材料', 'system', NOW(), 'system', NOW()),
(24, 3, 16, '3M-467', '3M 467胶带', '200mm×50m', 'm²', 0.0060, 3, NULL, NULL, NULL, 60, 100, 'back_adhesive', NULL, 'buy', NULL, 5, '背胶材料', 'system', NOW(), 'system', NOW()),
(25, 3, 6, 'SILVER-A', '导电银浆A型', '1kg/瓶', 'kg', 0.0015, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 6, '线路印刷用银浆', 'system', NOW(), 'system', NOW()),
(26, 3, 8, 'INS-WHITE', '白色绝缘油墨', '1kg/罐', 'kg', 0.0010, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 7, '绝缘层油墨', 'system', NOW(), 'system', NOW()),
(27, 3, 13, 'INK-WHITE', '白色油墨', '1kg/罐', 'kg', 0.0008, 10, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'buy', NULL, 8, '面板底色印刷', 'system', NOW(), 'system', NOW()),
(28, 3, 21, 'CONN-ZIF', 'ZIF连接器', '8pin/0.5mm间距', 'pcs', 1.0000, 0, NULL, NULL, NULL, NULL, NULL, NULL, 'J1', 'buy', NULL, 9, '输出连接器', 'system', NOW(), 'system', NOW());

-- ==================== 5. 工艺路线数据 ====================
-- 产品1工艺路线（6键薄膜开关）
INSERT IGNORE INTO product_routing (routing_id, routing_code, routing_name, routing_type, product_id, product_code, product_name, routing_version, is_current, approve_status, total_labor_hours, total_machine_hours, process_count, description, remark, create_by, create_time, update_by, update_time) VALUES
(1, 'RT-FS001-V1', '6键薄膜开关工艺路线 V1.0', 0, 1, 'FS-2024-001', '6键薄膜开关面板', 'V1.0', 1, 1, 6.30, 5.30, 12, '6键薄膜开关标准生产工艺路线', NULL, 'system', NOW(), 'system', NOW()),
(2, 'RT-FS002-V1', '12键背光薄膜开关工艺路线 V1.0', 0, 2, 'FS-2024-002', '12键背光薄膜开关', 'V1.0', 1, 1, 8.80, 7.80, 14, '12键背光薄膜开关标准生产工艺路线', NULL, 'system', NOW(), 'system', NOW()),
(3, 'RT-FS003-V1', '4键触摸薄膜开关工艺路线 V1.0', 0, 3, 'FS-2024-003', '4键触摸薄膜开关', 'V1.0', 1, 0, 5.80, 4.80, 11, '4键触摸薄膜开关标准生产工艺路线', NULL, 'system', NOW(), 'system', NOW());

-- 更新产品的当前工艺路线ID
UPDATE product SET current_route_id = 1 WHERE product_id = 1;
UPDATE product SET current_route_id = 2 WHERE product_id = 2;
UPDATE product SET current_route_id = 3 WHERE product_id = 3;

-- ==================== 6. 工艺路线明细数据 ====================
-- 产品1工艺路线明细（6键薄膜开关）
INSERT IGNORE INTO product_routing_item (item_id, routing_id, process_id, process_order, custom_labor_hours, custom_machine_hours, custom_process_params, description, create_time, update_time) VALUES
(1, 1, 1, 1, 0.50, 0.00, '{"检验标准":"来料检验标准V1.0"}', '来料检验', NOW(), NOW()),
(2, 1, 2, 2, 0.30, 0.00, '{"菲林编号":"FL-FS001-OV","菲林类型":"面板菲林"}', '面板菲林准备', NOW(), NOW()),
(3, 1, 3, 3, 0.50, 0.00, '{"油墨型号":"INK-BLACK","颜色":"黑色"}', '黑色油墨调配', NOW(), NOW()),
(4, 1, 5, 4, 1.00, 1.00, '{"颜色":"黑色","网版":"SC-FS001-OV"}', '面板丝印（黑色文字）', NOW(), NOW()),
(5, 1, 6, 5, 1.50, 1.50, '{"银浆型号":"SILVER-A","网版":"SC-FS001-UC"}', '上层线路印刷', NOW(), NOW()),
(6, 1, 7, 6, 0.80, 0.80, '{"绝缘油墨":"INS-WHITE","网版":"SC-FS001-INS"}', '绝缘层印刷', NOW(), NOW()),
(7, 1, 6, 7, 1.50, 1.50, '{"银浆型号":"SILVER-A","网版":"SC-FS001-LC"}', '下层线路印刷', NOW(), NOW()),
(8, 1, 9, 8, 0.80, 0.80, '{"模具编号":"DIE-FS001-OV"}', '面板模切', NOW(), NOW()),
(9, 1, 10, 9, 0.70, 0.70, '{"模具编号":"DIE-FS001-UC"}', '线路层模切', NOW(), NOW()),
(10, 1, 14, 10, 0.80, 0.50, '{"对位精度":"0.15mm"}', '总成贴合', NOW(), NOW()),
(11, 1, 17, 11, 0.50, 0.50, '{"测试项目":"导通测试+绝缘测试"}', '电性能测试', NOW(), NOW()),
(12, 1, 22, 12, 0.20, 0.00, '{"包装方式":"防静电袋+纸箱"}', '包装', NOW(), NOW());

-- 产品2工艺路线明细（12键背光薄膜开关）
INSERT IGNORE INTO product_routing_item (item_id, routing_id, process_id, process_order, custom_labor_hours, custom_machine_hours, custom_process_params, description, create_time, update_time) VALUES
(13, 2, 1, 1, 0.50, 0.00, '{"检验标准":"来料检验标准V1.0"}', '来料检验', NOW(), NOW()),
(14, 2, 2, 2, 0.30, 0.00, '{"菲林编号":"FL-FS002-OV","菲林类型":"面板菲林"}', '面板菲林准备', NOW(), NOW()),
(15, 2, 3, 3, 0.50, 0.00, '{"油墨型号":"INK-BLACK","颜色":"黑色"}', '黑色油墨调配', NOW(), NOW()),
(16, 2, 5, 4, 1.20, 1.20, '{"颜色":"黑色","网版":"SC-FS002-OV"}', '面板丝印（黑色文字）', NOW(), NOW()),
(17, 2, 6, 5, 1.80, 1.80, '{"银浆型号":"SILVER-B","网版":"SC-FS002-UC"}', '上层线路印刷', NOW(), NOW()),
(18, 2, 7, 6, 1.00, 1.00, '{"绝缘油墨":"INS-WHITE","网版":"SC-FS002-INS"}', '绝缘层印刷', NOW(), NOW()),
(19, 2, 6, 7, 1.80, 1.80, '{"银浆型号":"SILVER-B","网版":"SC-FS002-LC"}', '下层线路印刷', NOW(), NOW()),
(20, 2, 9, 8, 1.00, 1.00, '{"模具编号":"DIE-FS002-OV"}', '面板模切', NOW(), NOW()),
(21, 2, 10, 9, 0.80, 0.80, '{"模具编号":"DIE-FS002-UC"}', '线路层模切', NOW(), NOW()),
(22, 2, 14, 10, 1.00, 0.50, '{"对位精度":"0.15mm"}', '总成贴合', NOW(), NOW()),
(23, 2, 17, 11, 0.60, 0.60, '{"测试项目":"导通测试+绝缘测试"}', '电性能测试', NOW(), NOW()),
(24, 2, 18, 12, 0.30, 0.00, '{"检验标准":"外观检验标准V1.0"}', '外观检验', NOW(), NOW()),
(25, 2, 22, 13, 0.20, 0.00, '{"包装方式":"防静电袋+珍珠棉+纸箱"}', '包装', NOW(), NOW()),
(26, 2, 23, 14, 0.30, 0.00, '{"检验标准":"OQC检验标准V1.0"}', '最终检验（OQC）', NOW(), NOW());

-- 产品3工艺路线明细（4键触摸薄膜开关）
INSERT IGNORE INTO product_routing_item (item_id, routing_id, process_id, process_order, custom_labor_hours, custom_machine_hours, custom_process_params, description, create_time, update_time) VALUES
(27, 3, 1, 1, 0.50, 0.00, '{"检验标准":"来料检验标准V1.0"}', '来料检验', NOW(), NOW()),
(28, 3, 2, 2, 0.30, 0.00, '{"菲林编号":"FL-FS003-OV","菲林类型":"面板菲林"}', '面板菲林准备', NOW(), NOW()),
(29, 3, 3, 3, 0.50, 0.00, '{"油墨型号":"INK-WHITE","颜色":"白色"}', '白色油墨调配', NOW(), NOW()),
(30, 3, 5, 4, 0.80, 0.80, '{"颜色":"白色","网版":"SC-FS003-OV"}', '面板丝印（白色底色）', NOW(), NOW()),
(31, 3, 6, 5, 1.20, 1.20, '{"银浆型号":"SILVER-A","网版":"SC-FS003-UC"}', '上层线路印刷', NOW(), NOW()),
(32, 3, 7, 6, 0.60, 0.60, '{"绝缘油墨":"INS-WHITE","网版":"SC-FS003-INS"}', '绝缘层印刷', NOW(), NOW()),
(33, 3, 6, 7, 1.20, 1.20, '{"银浆型号":"SILVER-A","网版":"SC-FS003-LC"}', '下层线路印刷', NOW(), NOW()),
(34, 3, 9, 8, 0.60, 0.60, '{"模具编号":"DIE-FS003-OV"}', '面板模切', NOW(), NOW()),
(35, 3, 10, 9, 0.60, 0.60, '{"模具编号":"DIE-FS003-UC"}', '线路层模切', NOW(), NOW()),
(36, 3, 14, 10, 0.60, 0.50, '{"对位精度":"0.15mm"}', '总成贴合', NOW(), NOW()),
(37, 3, 17, 11, 0.50, 0.50, '{"测试项目":"导通测试+绝缘测试+触摸灵敏度"}', '电性能测试', NOW(), NOW());

-- ============================================================
-- 数据验证
-- ============================================================
-- SELECT 'product_category' AS table_name, COUNT(*) AS count FROM product_category
-- UNION ALL SELECT 'product', COUNT(*) FROM product
-- UNION ALL SELECT 'product_bom', COUNT(*) FROM product_bom
-- UNION ALL SELECT 'product_bom_item', COUNT(*) FROM product_bom_item
-- UNION ALL SELECT 'product_routing', COUNT(*) FROM product_routing
-- UNION ALL SELECT 'product_routing_item', COUNT(*) FROM product_routing_item;
