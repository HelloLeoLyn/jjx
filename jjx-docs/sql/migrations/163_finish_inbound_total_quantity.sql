-- ============================================================================
-- 163: 完工入库单表头汇总补齐（total_quantity 恒为 0 的历史行）
-- 任务：dev-20260921-031（来源：核查 FINISH-WO-PL2609210001-01）
-- 风险：低（只改表头汇总列，不碰明细/库存/流水；幂等可复跑）
-- ============================================================================
USE `jjx_erp_db`;

-- 背景：完工入库两条建单路径（createFromProduction / syncFinishInbound）漏写表头汇总，
--       导致入库作业列表与详情「总数量」显示 0（实单 FINISH-WO-PL2609210001-01：明细 2 / 表头 0）。
-- 口径：表头 total_quantity = 该单明细 quantity 合计；total_amount = 明细 amount 合计。
-- 幂等：只处理 total_quantity 为 0/NULL 且明细合计 > 0 的行，复跑第二次为 0 影响。
UPDATE inventory_inbound_order o
JOIN (
    SELECT inbound_id,
           COALESCE(SUM(quantity), 0)          AS qty,
           COALESCE(SUM(COALESCE(amount, 0)), 0) AS amt
    FROM inventory_inbound_item
    GROUP BY inbound_id
) i ON i.inbound_id = o.inbound_id
SET o.total_quantity = i.qty,
    o.total_amount   = i.amt,
    o.update_by      = 'Hermes',
    o.update_time    = NOW()
WHERE o.inbound_type = 'PRODUCTION_FINISH'
  AND (o.total_quantity = 0 OR o.total_quantity IS NULL)
  AND i.qty > 0;

-- 核验：完工入库单表头与明细应一致（本单应显示 2.0000 / 2.0000）
SELECT '核验：完工入库单表头 vs 明细' AS check_point;
SELECT o.inbound_no,
       o.total_quantity AS 表头数量,
       (SELECT COALESCE(SUM(quantity), 0) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id) AS 明细合计,
       o.order_status
FROM inventory_inbound_order o
WHERE o.inbound_type = 'PRODUCTION_FINISH'
ORDER BY o.inbound_id;
