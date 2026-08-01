-- ============================================================
-- 真实业务测试数据集（DEV-463）
-- 目标：能跑通 询价→报价→样品→量产→订单→采购→生产→发货 全链路
-- ============================================================

-- 1. 标准工序（薄膜开关 7 道工序）
INSERT INTO engineering_standard_process (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours, equipment_type, is_enabled, display_order)
VALUES
('PRT-001', '印刷', 'PRINTING', 'M', 0.5, 0.5, '丝印机', 1, 1),
('CUT-001', '冲切', 'CUTTING', 'M', 0.3, 0.4, '冲切机', 1, 2),
('LAM-001', '贴合', 'LAMINATING', 'M', 0.4, 0.4, '贴合机', 1, 3),
('SMT-001', 'SMT贴片', 'SMT', 'M', 0.6, 0.8, '贴片机', 1, 4),
('ASM-001', '装配', 'ASSEMBLY', 'M', 0.4, 0.2, '装配台', 1, 5),
('TST-001', '测试', 'TESTING', 'Q', 0.3, 0.3, '测试台', 1, 6),
('PKG-001', '包装', 'PACKAGING', 'M', 0.2, 0.1, '包装台', 1, 7);

-- 2. 仓库 + 库位
INSERT INTO inventory_warehouse (warehouse_code, warehouse_name, warehouse_type, location, manager, status)
VALUES
('WH-01', '原材料仓', 'raw', 'A栋1F', '仓管0', 0),
('WH-02', '成品仓', 'finished', 'A栋2F', '仓管0', 0);

INSERT INTO inventory_storage_location (warehouse_id, location_code, location_name, location_type, status)
SELECT warehouse_id, CONCAT(warehouse_code, '-A01'), CONCAT(warehouse_name, '-A01'), 'normal', 0 FROM inventory_warehouse;

-- 3. 供应商（3 家：原材料/设备/辅料）
INSERT INTO purchase_supplier (supplier_code, supplier_name, supplier_type, contact_person, phone, email, address, payment_terms, status)
VALUES
('SUP-001', '东莞薄膜材料有限公司', 'M', '王强', '13800138101', 'wangq@film-materials.com', '东莞市长安镇工业区', 'NET_30', 1),
('SUP-002', '深圳金手指电子', 'E', '李明', '13800138102', 'liming@jsz-elec.com', '深圳市宝安区', 'NET_15', 1),
('SUP-003', '惠州胶粘制品厂', 'M', '陈芳', '13800138103', 'chenfang@adhesive.com', '惠州市仲恺区', 'NET_30', 1);

-- 4. 物料（薄膜开关原材料：面板膜/线路层/胶层/背胶/连接器）
INSERT INTO inventory_material (material_code, material_name, material_type, process_group, specification, unit, safe_stock, max_stock, reorder_point, standard_price, lead_time, supplier_id, supplier_name, status)
VALUES
('MAT-001', 'PET面板膜 0.25mm', 'R', 'M', '0.25mm x 500mm卷', 'M', 100, 500, 80, 2.50, 7, 1, '东莞薄膜材料有限公司', 1),
('MAT-002', '银浆导电线路膜', 'R', 'M', '银浆印刷线路', 'M', 80, 400, 60, 4.20, 10, 1, '东莞薄膜材料有限公司', 1),
('MAT-003', '3M双面胶 0.1mm', 'R', 'M', '0.1mm间隔层', 'M', 120, 600, 90, 1.80, 5, 3, '惠州胶粘制品厂', 1),
('MAT-004', '背胶层 0.05mm', 'R', 'M', '0.05mm背胶', 'M', 100, 500, 70, 1.20, 5, 3, '惠州胶粘制品厂', 1),
('MAT-005', 'FPC连接器 4pin', 'R', 'M', '4pin FPC', 'PCS', 200, 1000, 150, 0.80, 7, 2, '深圳金手指电子', 1),
('MAT-006', 'LED背光片', 'R', 'M', '3.5x2.8mm', 'PCS', 150, 800, 100, 1.50, 10, 2, '深圳金手指电子', 1);
