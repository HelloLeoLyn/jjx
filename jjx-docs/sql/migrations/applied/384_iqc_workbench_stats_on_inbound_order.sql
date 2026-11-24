-- 384_iqc_workbench_stats_on_inbound_order.sql
-- 任务码：dev-20261124-001
-- 目的：IQC 工作台列表统计（材料数/已检数/待审核/待检验/待复检/剩余可处置量/待报废/待返工/已批准/FAIL 行数）
--       由「列表实时相关子查询」改为 inventory_inbound_order 主表列，写时刷新、读时直读。
-- risk：中（仅新增 10 个可空列 + 一次性全表回填；不改旧列、不删数据）
-- 回滚：ALTER TABLE inventory_inbound_order DROP COLUMN iqc_material_count, DROP COLUMN iqc_inspected_count,
--       DROP COLUMN iqc_pending_review_count, DROP COLUMN iqc_pending_inspection_count,
--       DROP COLUMN iqc_pending_reinspection_count, DROP COLUMN iqc_remaining_disposition_qty,
--       DROP COLUMN iqc_pending_scrap_count, DROP COLUMN iqc_pending_rework_count,
--       DROP COLUMN iqc_approved_count, DROP COLUMN iqc_fail_row_count;
-- 幂等：加列走 information_schema 判断；回填为覆盖式 UPDATE，可重复执行。

SET NAMES utf8mb4;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_material_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_material_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-材料数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_inspected_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_inspected_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-已检数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_pending_review_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_pending_review_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-待审核数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_pending_inspection_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_pending_inspection_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-待检验数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_pending_reinspection_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_pending_reinspection_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-待复检数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_remaining_disposition_qty'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_remaining_disposition_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT ''IQC统计-剩余可处置量''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_pending_scrap_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_pending_scrap_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-待报废审批数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_pending_rework_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_pending_rework_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-待返工数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_approved_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_approved_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-已批准数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_fail_row_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_fail_row_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-FAIL行数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_stats_time'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_stats_time DATETIME NULL COMMENT ''IQC统计-刷新时间''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 回填：与 InventoryInboundOrderMapper 原 selectIqcPendingPage 的子查询口径保持完全一致。
-- 「当前检验批」判定：优先取最近一张 REWORK 处置单关联的 lot，否则取入库明细自己的 lot_id。
UPDATE inventory_inbound_order o
SET
  o.iqc_material_count = (SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id),
  o.iqc_inspected_count = (SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.lot_id IS NOT NULL),
  o.iqc_pending_review_count = (
    SELECT COUNT(*) FROM inventory_inbound_item i
    LEFT JOIN quality_lot q ON q.lot_id = COALESCE((SELECT d.lot_id FROM inventory_iqc_disposition_order d
        WHERE d.inbound_item_id=i.item_id AND d.action='REWORK' AND d.lot_id IS NOT NULL
        AND d.status IN ('PENDING_REINSPECTION','COMPLETED') ORDER BY d.disposition_id DESC LIMIT 1), i.lot_id)
    WHERE i.inbound_id=o.inbound_id AND q.review_status='PENDING'),
  o.iqc_pending_inspection_count = (
    SELECT COUNT(*) FROM inventory_inbound_item i
    LEFT JOIN quality_lot q ON q.lot_id = COALESCE((SELECT d.lot_id FROM inventory_iqc_disposition_order d
        WHERE d.inbound_item_id=i.item_id AND d.action='REWORK' AND d.lot_id IS NOT NULL
        AND d.status IN ('PENDING_REINSPECTION','COMPLETED') ORDER BY d.disposition_id DESC LIMIT 1), i.lot_id)
    WHERE i.inbound_id=o.inbound_id AND (q.review_status IS NULL OR q.review_status IN ('DRAFT','REJECTED')) AND q.parent_lot_id IS NULL),
  o.iqc_pending_reinspection_count = (
    SELECT COUNT(*) FROM inventory_inbound_item i
    LEFT JOIN quality_lot q ON q.lot_id = COALESCE((SELECT d.lot_id FROM inventory_iqc_disposition_order d
        WHERE d.inbound_item_id=i.item_id AND d.action='REWORK' AND d.lot_id IS NOT NULL
        AND d.status IN ('PENDING_REINSPECTION','COMPLETED') ORDER BY d.disposition_id DESC LIMIT 1), i.lot_id)
    WHERE i.inbound_id=o.inbound_id AND (q.review_status IS NULL OR q.review_status IN ('DRAFT','REJECTED')) AND q.parent_lot_id IS NOT NULL),
  o.iqc_remaining_disposition_qty = (SELECT COALESCE(SUM(GREATEST(q.remaining_quantity, 0)),0) FROM inventory_iqc_quarantine q WHERE q.inbound_id=o.inbound_id),
  o.iqc_pending_scrap_count = (SELECT COUNT(*) FROM inventory_iqc_disposition_order d WHERE d.inbound_id=o.inbound_id AND d.action='SCRAP' AND d.status='PENDING_APPROVAL'),
  o.iqc_pending_rework_count = (SELECT COUNT(*) FROM inventory_iqc_disposition_order d WHERE d.inbound_id=o.inbound_id AND d.action='REWORK' AND d.status='CREATED'),
  o.iqc_approved_count = (SELECT COUNT(*) FROM inventory_inbound_item i JOIN quality_lot q ON q.lot_id = i.lot_id WHERE i.inbound_id = o.inbound_id AND q.review_status = 'APPROVED'),
  o.iqc_fail_row_count = (SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.inspection_result = 'FAIL'),
  o.iqc_stats_time = NOW();
