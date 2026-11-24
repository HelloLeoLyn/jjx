-- 385_iqc_stats_material_vs_rows.sql
-- 任务码：dev-20261124-002
-- 目的：IQC 工作台统计口径拆分 ——
--       材料数 = 不同材料去重（COUNT DISTINCT material_id）
--       已检数 = 已有检验批的不同材料数
--       新增 明细行数 = 明细行数（按卷拆行后是行数）
-- risk：低（新增 1 列 + 覆盖式重算既有统计列）
-- 回滚：ALTER TABLE inventory_inbound_order DROP COLUMN iqc_item_count;
--       并把 iqc_material_count / iqc_inspected_count 的口径恢复为按行（见迁移 384 的回填语句）。
-- 幂等：加列走 information_schema 判断；回填为覆盖式 UPDATE，可重复执行。

SET NAMES utf8mb4;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_order' AND COLUMN_NAME='iqc_item_count'),
  'SELECT 1', 'ALTER TABLE inventory_inbound_order ADD COLUMN iqc_item_count INT NOT NULL DEFAULT 0 COMMENT ''IQC统计-明细行数''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 回填：材料口径去重；行数独立统计；其余列沿用 384 的口径一并重算，保证同一行口径一致。
UPDATE inventory_inbound_order o
SET
  o.iqc_material_count = (SELECT COUNT(DISTINCT i.material_id) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.material_id IS NOT NULL),
  o.iqc_item_count = (SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id),
  o.iqc_inspected_count = (SELECT COUNT(DISTINCT i.material_id) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.material_id IS NOT NULL AND i.lot_id IS NOT NULL),
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
