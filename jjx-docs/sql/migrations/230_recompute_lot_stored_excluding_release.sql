-- risk: high
-- task: dev-20260929-004
-- 订正存量：让步放行件（IQC_RELEASE）曾被计入 quality_lot.stored_quantity，
--   导致「入库/放行累计 stored_quantity ≤ pass_quantity」（check-inbound-lot-integrity.sh 规则⑧）被打破
--   （实测 IN260929005 / QL260929004：pass 40、stored 45）。
-- 口径（dev-20260929-004，与代码 syncQualityLotStored 同步）：
--   stored_quantity 只统计**合格件入库**（排除 IQC_RELEASE 单据的已过账净额）；
--   让步件在库存侧由库存流水 + 处置单（PENDING_INBOUND / COMPLETED）承载。
-- 幂等：按「未取消单据、排除 IQC_RELEASE」重算目标值，仅在与现值不同时更新。
UPDATE quality_lot l
JOIN (
  SELECT li.lot_id,
         COALESCE(SUM(li.posted_quantity), 0) AS target_stored
    FROM inventory_inbound_item li
    JOIN inventory_inbound_order o
      ON o.inbound_id = li.inbound_id
     AND o.order_status <> 9
     AND o.source_type <> 'IQC_RELEASE'
     AND o.inbound_type <> 'iqc_release'
   WHERE li.lot_id IS NOT NULL
   GROUP BY li.lot_id
) t ON t.lot_id = l.lot_id
SET l.stored_quantity = t.target_stored,
    l.update_time = NOW()
WHERE COALESCE(l.stored_quantity, 0) <> t.target_stored;
