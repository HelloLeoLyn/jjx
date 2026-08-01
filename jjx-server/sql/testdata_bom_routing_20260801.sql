-- ============================================================
-- BOM + 工艺路线 测试数据（DEV-463）
-- 已批准(approve_status=3) 的 BOM/工艺路线，支撑 生产工单 创建
-- ============================================================

-- 1. BOM 主表（MBS-001 标准面板 / MBS-003 背光型）
INSERT INTO engineering_bom (bom_code, product_id, bom_version, bom_type, is_current, effective_date, approve_status, approve_by, approve_time, bom_name, create_by)
VALUES
('BOM-MBS001-V1', 1, 'V1', 'manufacturing', 1, '2026-08-01', 3, 'gongcheng0', NOW(), '薄膜开关-标准面板 BOM', 'gongcheng0'),
('BOM-MBS003-V1', 3, 'V1', 'manufacturing', 1, '2026-08-01', 3, 'gongcheng0', NOW(), '薄膜开关-背光型 BOM', 'gongcheng0');

-- 2. BOM 明细（MBS-001：面板膜/线路膜/双面胶/背胶）
INSERT INTO engineering_bom_item (bom_id, material_id, material_code, material_name, quantity, unit, loss_rate, source_type, item_order)
SELECT b.bom_id, m.material_id, m.material_code, m.material_name,
       CASE m.material_code
         WHEN 'MAT-001' THEN 0.01 WHEN 'MAT-002' THEN 0.01
         WHEN 'MAT-003' THEN 0.01 WHEN 'MAT-004' THEN 0.01 END,
       'M', 5.00, 'buy', 1
FROM engineering_bom b, inventory_material m
WHERE b.bom_code='BOM-MBS001-V1' AND m.material_code IN ('MAT-001','MAT-002','MAT-003','MAT-004');

-- 3. BOM 明细（MBS-003：背光型 = 标准面板 + LED背光片 + FPC连接器）
INSERT INTO engineering_bom_item (bom_id, material_id, material_code, material_name, quantity, unit, loss_rate, source_type, item_order)
SELECT b.bom_id, m.material_id, m.material_code, m.material_name,
       CASE m.material_code
         WHEN 'MAT-001' THEN 0.01 WHEN 'MAT-002' THEN 0.01
         WHEN 'MAT-003' THEN 0.01 WHEN 'MAT-004' THEN 0.01
         WHEN 'MAT-005' THEN 1 WHEN 'MAT-006' THEN 1 END,
       CASE WHEN m.material_code IN ('MAT-005','MAT-006') THEN 'PCS' ELSE 'M' END,
       5.00, 'buy', 1
FROM engineering_bom b, inventory_material m
WHERE b.bom_code='BOM-MBS003-V1' AND m.material_code IN ('MAT-001','MAT-002','MAT-003','MAT-004','MAT-005','MAT-006');

-- 4. 工艺路线主表（MBS-001 / MBS-003：印刷→冲切→贴合→测试→包装）
INSERT INTO engineering_routing (routing_code, routing_name, product_id, product_code, product_name, routing_version, is_current, approve_status, total_labor_hours, total_machine_hours, process_count, create_by)
VALUES
('RTE-MBS001-V1', '标准面板生产工艺', 1, 'MBS-001', '薄膜开关-标准面板', 'V1', 1, 3, 1.70, 1.70, 5, 'gongcheng0'),
('RTE-MBS003-V1', '背光型生产工艺', 3, 'MBS-003', '薄膜开关-背光型', 'V1', 1, 3, 2.30, 2.50, 7, 'gongcheng0');

-- 5. 工艺路线明细（MBS-001：印刷/冲切/贴合/测试/包装）
INSERT INTO engineering_routing_item (routing_id, process_id, process_order, custom_labor_hours, custom_machine_hours, process_category)
SELECT r.routing_id, p.process_id, p.display_order, p.standard_labor_hours, p.standard_machine_hours, p.process_category
FROM engineering_routing r, engineering_standard_process p
WHERE r.routing_code='RTE-MBS001-V1' AND p.process_code IN ('PRT-001','CUT-001','LAM-001','TST-001','PKG-001');

-- 6. 工艺路线明细（MBS-003：全 7 道工序）
INSERT INTO engineering_routing_item (routing_id, process_id, process_order, custom_labor_hours, custom_machine_hours, process_category)
SELECT r.routing_id, p.process_id, p.display_order, p.standard_labor_hours, p.standard_machine_hours, p.process_category
FROM engineering_routing r, engineering_standard_process p
WHERE r.routing_code='RTE-MBS003-V1' AND p.process_code IN ('PRT-001','CUT-001','LAM-001','SMT-001','ASM-001','TST-001','PKG-001');
