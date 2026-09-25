-- Data repair for dev-20260924-037.
-- Guard backups are indexed in ../backups/backup-index.tsv before execution.
-- Restore only the source-row pointers from the original IQC lot and ORIGINAL
-- batch; child reinspection lineage remains on inventory_iqc_rework_order.
START TRANSACTION;

UPDATE inventory_inbound_item ii
JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
JOIN quality_lot l
  ON l.source_item_id = ii.item_id
 AND l.lot_type = 'IQC'
 AND l.parent_lot_id IS NULL
JOIN inventory_iqc_batch b
  ON b.source_inbound_item_id = ii.item_id
 AND b.batch_type = 'ORIGINAL'
SET ii.batch_no = b.batch_no,
    ii.sampled_quantity = l.inspected_quantity,
    ii.inspection_result = UPPER(l.result),
    ii.qualified_quantity = l.pass_quantity,
    ii.rejected_quantity = l.fail_quantity,
    ii.accepted_quantity = l.pass_quantity,
    ii.lot_id = l.lot_id,
    ii.iqc_batch_id = b.batch_id
WHERE o.inbound_no = 'IN260924001'
  AND ii.item_id = 1;

-- Rebuild the judgement counters on the two confirmed affected original
-- batches from their authoritative IQC lots. Disposition totals/statuses are
-- left unchanged because scrap approval remains pending.
UPDATE inventory_iqc_batch b
JOIN quality_lot l ON l.batch_no = b.batch_no AND l.lot_type = 'IQC'
SET b.processed_quantity = l.inspected_quantity,
    b.accepted_quantity = l.pass_quantity,
    b.rejected_quantity = l.fail_quantity,
    b.remaining_quantity = GREATEST(0, b.quantity - l.inspected_quantity)
WHERE b.batch_id IN (5, 6)
  AND b.batch_type = 'ORIGINAL';

COMMIT;
