-- dev-20260918-008：将 IQC 批次身份贯穿质量单据、库存和流水
ALTER TABLE `inventory_iqc_batch`
  ADD COLUMN `processed_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' AFTER `quantity`,
  ADD COLUMN `accepted_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' AFTER `processed_quantity`,
  ADD COLUMN `rejected_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' AFTER `accepted_quantity`,
  ADD COLUMN `scrapped_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' AFTER `rejected_quantity`,
  ADD COLUMN `remaining_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' AFTER `scrapped_quantity`;

ALTER TABLE `inventory_inbound_item` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_inbound_item_iqc_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_iqc_quarantine` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_iqc_quarantine_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_iqc_disposition_order` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_iqc_disposition_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_iqc_return_order` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_iqc_return_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_iqc_scrap_order` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_iqc_scrap_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_iqc_rework_order` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_iqc_rework_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_stock_item` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_stock_iqc_batch` (`iqc_batch_id`);
ALTER TABLE `inventory_transaction` ADD COLUMN `iqc_batch_id` bigint DEFAULT NULL AFTER `batch_no`, ADD KEY `idx_transaction_iqc_batch` (`iqc_batch_id`);

-- 开发库兼容回填：只为已有批次建立原始批次身份，不改变数量和业务状态。
INSERT INTO inventory_iqc_batch (material_id, batch_no, root_batch_no, batch_type, quantity, remaining_quantity, status, source_inbound_item_id, source_inspection_id)
SELECT x.material_id, x.batch_no, x.batch_no, 'ORIGINAL', COALESCE(x.quantity, 0), COALESCE(x.quantity, 0), 'SOURCE', x.item_id, x.inspection_id
FROM (
  SELECT material_id, batch_no, MIN(item_id) item_id, MAX(inspection_id) inspection_id, SUM(quantity) quantity
  FROM inventory_inbound_item
  WHERE batch_no IS NOT NULL AND batch_no <> ''
  GROUP BY material_id, batch_no
) x
LEFT JOIN inventory_iqc_batch b ON b.material_id <=> x.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = x.batch_no COLLATE utf8mb4_0900_ai_ci
WHERE b.batch_id IS NULL;

UPDATE inventory_inbound_item i JOIN inventory_iqc_batch b
  ON b.material_id <=> i.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = i.batch_no COLLATE utf8mb4_0900_ai_ci
SET i.iqc_batch_id = b.batch_id
WHERE i.batch_no IS NOT NULL AND i.batch_no <> '';
UPDATE inventory_iqc_quarantine q JOIN inventory_iqc_batch b
  ON b.material_id <=> q.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = q.batch_no COLLATE utf8mb4_0900_ai_ci
SET q.iqc_batch_id = b.batch_id;
UPDATE inventory_iqc_disposition_order d JOIN inventory_iqc_batch b
  ON b.material_id <=> (SELECT material_id FROM inventory_inbound_item i WHERE i.item_id = d.inbound_item_id)
 AND b.batch_no COLLATE utf8mb4_0900_ai_ci = d.batch_no COLLATE utf8mb4_0900_ai_ci
SET d.iqc_batch_id = b.batch_id;
UPDATE inventory_iqc_return_order r JOIN inventory_iqc_batch b
  ON b.material_id <=> r.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = r.batch_no COLLATE utf8mb4_0900_ai_ci
SET r.iqc_batch_id = b.batch_id;
UPDATE inventory_iqc_scrap_order s JOIN inventory_iqc_batch b
  ON b.material_id <=> s.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = s.batch_no COLLATE utf8mb4_0900_ai_ci
SET s.iqc_batch_id = b.batch_id;
UPDATE inventory_iqc_rework_order r JOIN inventory_iqc_batch b
  ON b.material_id <=> (SELECT material_id FROM inventory_inbound_item i WHERE i.item_id = r.inbound_item_id)
 AND b.batch_no COLLATE utf8mb4_0900_ai_ci = r.batch_no COLLATE utf8mb4_0900_ai_ci
SET r.iqc_batch_id = b.batch_id;
UPDATE inventory_stock_item s JOIN inventory_iqc_batch b
  ON b.material_id <=> s.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = s.batch_no COLLATE utf8mb4_0900_ai_ci
SET s.iqc_batch_id = b.batch_id;
UPDATE inventory_transaction t JOIN inventory_iqc_batch b
  ON b.material_id <=> t.material_id AND b.batch_no COLLATE utf8mb4_0900_ai_ci = t.batch_no COLLATE utf8mb4_0900_ai_ci
SET t.iqc_batch_id = b.batch_id;
