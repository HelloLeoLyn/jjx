-- ============================================================
-- 生产管理业务数据清理脚本（Acceptance environment cleanup）
-- 日期：2026-08-20
-- 说明：清除生产域业务数据（验收测试产生），保留基础字典与关联数据
-- 保留：production_tooling(模具/治具字典)、production_equipment(空)、
--       库存(采购入库 3 条 + 成品物料)、销售订单 1、产品档案
-- 执行前请先运行下方 SELECT 确认数据量；执行为 DELETE（非 TRUNCATE，可留档）
-- ============================================================

-- ============ 0. 清理前确认（SELECT） ============
SELECT 'production_order' t, COUNT(*) c FROM production_order
UNION ALL SELECT 'production_operation_execution', COUNT(*) FROM production_operation_execution
UNION ALL SELECT 'production_operation_record', COUNT(*) FROM production_operation_record
UNION ALL SELECT 'production_dispatch', COUNT(*) FROM production_dispatch
UNION ALL SELECT 'production_dispatch_node', COUNT(*) FROM production_dispatch_node
UNION ALL SELECT 'production_dispatch_log', COUNT(*) FROM production_dispatch_log
UNION ALL SELECT 'production_execution_assignment', COUNT(*) FROM production_execution_assignment
UNION ALL SELECT 'production_work_report', COUNT(*) FROM production_work_report
UNION ALL SELECT 'production_quality_inspection', COUNT(*) FROM production_quality_inspection
UNION ALL SELECT 'production_quality_inspection_item', COUNT(*) FROM production_quality_inspection_item
UNION ALL SELECT 'production_trace_log', COUNT(*) FROM production_trace_log;

-- ============ 1. 派工链（子表先删） ============
DELETE FROM production_dispatch_log;
DELETE FROM production_dispatch_node;
DELETE FROM production_dispatch;

-- ============ 2. 作业分配 / 报工 ============
DELETE FROM production_execution_assignment;
DELETE FROM production_work_report;

-- ============ 3. 工序执行 ============
DELETE FROM production_operation_record;
DELETE FROM production_operation_execution;

-- ============ 4. 质检（含明细） ============
DELETE FROM production_quality_inspection_item;
DELETE FROM production_quality_inspection;

-- ============ 5. Trace 事件日志 ============
DELETE FROM production_trace_log;

-- ============ 6. 生产工单（含计划单） ============
DELETE FROM production_order;

-- ============ 7. 清理后确认（应为 0） ============
SELECT 'production_order' t, COUNT(*) c FROM production_order
UNION ALL SELECT 'production_operation_execution', COUNT(*) FROM production_operation_execution
UNION ALL SELECT 'production_operation_record', COUNT(*) FROM production_operation_record
UNION ALL SELECT 'production_dispatch', COUNT(*) FROM production_dispatch
UNION ALL SELECT 'production_dispatch_node', COUNT(*) FROM production_dispatch_node
UNION ALL SELECT 'production_dispatch_log', COUNT(*) FROM production_dispatch_log
UNION ALL SELECT 'production_execution_assignment', COUNT(*) FROM production_execution_assignment
UNION ALL SELECT 'production_work_report', COUNT(*) FROM production_work_report
UNION ALL SELECT 'production_quality_inspection', COUNT(*) FROM production_quality_inspection
UNION ALL SELECT 'production_quality_inspection_item', COUNT(*) FROM production_quality_inspection_item
UNION ALL SELECT 'production_trace_log', COUNT(*) FROM production_trace_log;
