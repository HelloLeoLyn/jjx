-- ============================================================================
-- 124: 质量重构——存量测试数据重置 ——批4 / dev-20260917-013
-- 背景：未上线测试库。旧模型遗留：FQC 4 张（含 1 张 pending 空单）、复检导致同批重复入库
--       （WO-PL2609160002-01 库存 700 / 计划 500）、IQC 隔离 1 行（PO202609160003 手工补齐）。
-- 口径（013）：清理旧模型质量数据 + 把"超入"的账做平（库存归零、工单成品归零），保留历史流水；
--       随后由用户重启后端、用**新工单**走新流程做 E2E（旧单已完工，不在老单上重跑状态机）。
-- 幂等：仅命中"当前值不等于目标值"的行；重复执行匹配 0 行。
-- ============================================================================
USE `jjx_erp_db`;

-- ① 库存：重置该批次成品库存为 0，并写 ADJUST 凭证（保历史、账实一致）
INSERT INTO inventory_transaction
    (inventory_item_id, material_id, material_code, material_name, warehouse_id, location_id, transaction_type,
     source_type, source_id, source_no, batch_no, quantity, before_quantity, after_quantity, unit_cost, amount,
     transaction_time, remark, create_by, create_time)
SELECT s.inventory_item_id, s.material_id, s.material_code, s.material_name, s.warehouse_id, s.location_id,
       'ADJUST', 'PRODUCTION', 3, 'WO-PL2609160002-01', s.batch_no,
       -s.quantity, s.quantity, 0, s.unit_cost, NULL, NOW(),
       '质量重构重置：复检重复入库，库存归零（dev-20260917-013）', 'Hermes', NOW()
FROM inventory_stock_item s
WHERE s.material_code = 'JST001POOO' AND s.batch_no = 'BATCH-WO-PL2609160002-01' AND s.quantity <> 0;

UPDATE inventory_stock_item
SET quantity = 0, reserved_quantity = 0
WHERE material_code = 'JST001POOO' AND batch_no = 'BATCH-WO-PL2609160002-01' AND quantity <> 0;

-- ② 作废该工单 3 张完工入库单（FINISH-...-FQC-4/5/6），使新流程可重新生成一张完工入库单
UPDATE inventory_inbound_order
SET order_status = 9
WHERE source_type = 'PRODUCTION' AND source_id = 3 AND order_status <> 9;

-- ③ 工单成品口径归零（成品数由成品检验批判定写入；重置后由新流程重算）
UPDATE production_order
SET completed_quantity = 0, finished_quantity = 0, remaining_quantity = planned_quantity, quality_inspection_id = NULL
WHERE order_id = 3 AND (finished_quantity <> 0 OR completed_quantity <> 0);

-- ④ 清理旧模型质量数据（新模型 quality_lot / quality_ncr 为准）
DELETE FROM production_quality_inspection_item;
DELETE FROM production_quality_inspection;
DELETE FROM inventory_iqc_quarantine;
DELETE FROM quality_lot_item;
DELETE FROM quality_lot;
DELETE FROM quality_ncr_action;
DELETE FROM quality_ncr;

-- ⑤ 抽样方案示例（AQL 1.0、检验水平 II；幂等按 plan_name）
INSERT INTO quality_sampling_plan (plan_name, lot_type, aql_value, inspection_level, lot_min, lot_max, sample_quantity, accept_number, reject_number, is_enabled, create_by, remark)
SELECT 'AQL1.0 II级 91-150', 'IQC', 1.000, 'II', 91, 150, 20, 1, 2, 1, 'Hermes', '示例配置，可按实际标准调整'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT plan_name FROM quality_sampling_plan) t WHERE t.plan_name = 'AQL1.0 II级 91-150');
INSERT INTO quality_sampling_plan (plan_name, lot_type, aql_value, inspection_level, lot_min, lot_max, sample_quantity, accept_number, reject_number, is_enabled, create_by, remark)
SELECT 'AQL1.0 II级 151-280', 'IQC', 1.000, 'II', 151, 280, 32, 1, 2, 1, 'Hermes', '示例配置，可按实际标准调整'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT plan_name FROM quality_sampling_plan) t WHERE t.plan_name = 'AQL1.0 II级 151-280');
INSERT INTO quality_sampling_plan (plan_name, lot_type, aql_value, inspection_level, lot_min, lot_max, sample_quantity, accept_number, reject_number, is_enabled, create_by, remark)
SELECT 'AQL1.0 II级 281-500', 'IQC', 1.000, 'II', 281, 500, 50, 1, 2, 1, 'Hermes', '示例配置，可按实际标准调整'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT plan_name FROM quality_sampling_plan) t WHERE t.plan_name = 'AQL1.0 II级 281-500');
INSERT INTO quality_sampling_plan (plan_name, lot_type, aql_value, inspection_level, lot_min, lot_max, sample_quantity, accept_number, reject_number, is_enabled, create_by, remark)
SELECT 'AQL1.0 II级 501-1200', 'IQC', 1.000, 'II', 501, 1200, 80, 2, 3, 1, 'Hermes', '示例配置，可按实际标准调整'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT plan_name FROM quality_sampling_plan) t WHERE t.plan_name = 'AQL1.0 II级 501-1200');

-- ⑥ 核验
SELECT '核验：库存/工单/入库单/质量旧表 应为 0 / 0 / 已作废 / 空' AS check_point;
SELECT (SELECT COALESCE(SUM(quantity),0) FROM inventory_stock_item WHERE batch_no='BATCH-WO-PL2609160002-01') AS stock_qty,
       (SELECT finished_quantity FROM production_order WHERE order_id=3) AS order_finished,
       (SELECT remaining_quantity FROM production_order WHERE order_id=3) AS order_remaining,
       (SELECT COUNT(*) FROM inventory_inbound_order WHERE source_type='PRODUCTION' AND source_id=3 AND order_status=9) AS cancelled_inbound,
       (SELECT COUNT(*) FROM production_quality_inspection) AS old_inspections,
       (SELECT COUNT(*) FROM quality_lot) AS lots,
       (SELECT COUNT(*) FROM quality_ncr) AS ncrs,
       (SELECT COUNT(*) FROM quality_sampling_plan) AS sampling_plans;
