-- ============================================================
-- 生产模块初始化数据
-- 执行顺序：第6个执行（依赖系统模块、产品模块）
-- 包含：生产订单（生产计划+生产工单）
-- ============================================================

-- ==================== 1. 生产计划数据 ====================
INSERT IGNORE INTO production_order (order_id, order_no, order_type, parent_order_id, sales_order_id, sales_order_no, product_id, product_code, product_name, product_spec, product_unit, routing_id, routing_code, planned_quantity, completed_quantity, remaining_quantity, plan_start_date, plan_end_date, actual_start_time, actual_end_time, order_status, approval_status, approver_id, approver_name, approval_time, approval_remark, priority, department_id, department_name, material_cost, labor_cost, total_cost, create_by, create_time, update_by, update_time, remark) VALUES
-- 生产计划1：6键薄膜开关
(1, 'PLAN-2024-001', 'PLAN', NULL, NULL, NULL, 1, 'FS-2024-001', '6键薄膜开关面板', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6}', 'pcs', 1, 'RT-FS001-V1', 500.0000, 0.0000, 500.0000, '2024-07-01', '2024-07-15', NULL, NULL, 4, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'MEDIUM', 104, '生产部', 4250.0000, 3150.0000, 7400.0000, 'system', NOW(), 'system', NOW(), '6键薄膜开关生产计划，数量500pcs'),

-- 生产计划2：12键背光薄膜开关
(2, 'PLAN-2024-002', 'PLAN', NULL, NULL, NULL, 2, 'FS-2024-002', '12键背光薄膜开关', '{"dimensions":{"width":120,"height":160,"unit":"mm"},"keyCount":12}', 'pcs', 2, 'RT-FS002-V1', 200.0000, 0.0000, 200.0000, '2024-07-10', '2024-07-30', NULL, NULL, 4, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'HIGH', 104, '生产部', 3300.0000, 1760.0000, 5060.0000, 'system', NOW(), 'system', NOW(), '12键背光薄膜开关生产计划，数量200pcs'),

-- 生产计划3：4键触摸薄膜开关（草稿状态）
(3, 'PLAN-2024-003', 'PLAN', NULL, NULL, NULL, 3, 'FS-2024-003', '4键触摸薄膜开关', '{"dimensions":{"width":60,"height":100,"unit":"mm"},"keyCount":4}', 'pcs', 3, 'RT-FS003-V1', 300.0000, 0.0000, 300.0000, '2024-08-01', '2024-08-15', NULL, NULL, 0, 'PENDING', NULL, NULL, NULL, NULL, 'LOW', 104, '生产部', 3600.0000, 1740.0000, 5340.0000, 'system', NOW(), 'system', NOW(), '4键触摸薄膜开关生产计划（待审批）');

-- ==================== 2. 生产工单数据 ====================
INSERT IGNORE INTO production_order (order_id, order_no, order_type, parent_order_id, sales_order_id, sales_order_no, product_id, product_code, product_name, product_spec, product_unit, routing_id, routing_code, planned_quantity, completed_quantity, remaining_quantity, plan_start_date, plan_end_date, actual_start_time, actual_end_time, order_status, approval_status, approver_id, approver_name, approval_time, approval_remark, priority, department_id, department_name, material_cost, labor_cost, total_cost, create_by, create_time, update_by, update_time, remark) VALUES
-- 工单1：从计划1拆出的第一批
(4, 'WO-2024-001', 'WORK_ORDER', 1, NULL, NULL, 1, 'FS-2024-001', '6键薄膜开关面板', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6}', 'pcs', 1, 'RT-FS001-V1', 200.0000, 0.0000, 200.0000, '2024-07-01', '2024-07-08', NULL, NULL, 5, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'MEDIUM', 104, '生产部', 1700.0000, 1260.0000, 2960.0000, 'system', NOW(), 'system', NOW(), '6键薄膜开关第一批生产工单，数量200pcs'),

-- 工单2：从计划1拆出的第二批
(5, 'WO-2024-002', 'WORK_ORDER', 1, NULL, NULL, 1, 'FS-2024-001', '6键薄膜开关面板', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6}', 'pcs', 1, 'RT-FS001-V1', 300.0000, 0.0000, 300.0000, '2024-07-09', '2024-07-15', NULL, NULL, 5, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'MEDIUM', 104, '生产部', 2550.0000, 1890.0000, 4440.0000, 'system', NOW(), 'system', NOW(), '6键薄膜开关第二批生产工单，数量300pcs'),

-- 工单3：从计划2拆出的工单
(6, 'WO-2024-003', 'WORK_ORDER', 2, NULL, NULL, 2, 'FS-2024-002', '12键背光薄膜开关', '{"dimensions":{"width":120,"height":160,"unit":"mm"},"keyCount":12}', 'pcs', 2, 'RT-FS002-V1', 200.0000, 0.0000, 200.0000, '2024-07-10', '2024-07-30', NULL, NULL, 5, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'HIGH', 104, '生产部', 3300.0000, 1760.0000, 5060.0000, 'system', NOW(), 'system', NOW(), '12键背光薄膜开关生产工单，数量200pcs');

-- ============================================================
-- 数据验证
-- ============================================================
-- SELECT 'production_order' AS table_name, COUNT(*) AS count FROM production_order;
-- SELECT order_type, order_status, COUNT(*) AS count FROM production_order GROUP BY order_type, order_status;
