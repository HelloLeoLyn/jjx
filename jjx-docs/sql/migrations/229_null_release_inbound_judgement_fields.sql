-- risk: high
-- task: dev-20260929-004
-- 放行类入库行（IQC_RELEASE 让步接收 / IQC_REWORK 返工复检）不是质量判定行：
--   判定三字段（qualified_quantity / rejected_quantity / accepted_quantity）一律为 NULL。
-- 依据：history/iqc-disposition-truth-rootfix-dev-20260929-003.md §4.6（2026-09-29 定稿）。
--   写 0 会被任何 SUM/报表读成「合格 0、允收 0」的假事实；写放行量又会造出
--   accepted > pass 的跨桶混口径（即 IN260929005 lot1 关批偶然性的成因）。
-- 正常采购（PURCHASE）行的判定字段不动。
--
-- 说明：accepted_quantity 原为 NOT NULL DEFAULT 0，需先放开为可空（保留 DEFAULT 0，
--   以免影响未检采购行的既有默认行为）；posted/quantity 不动，入库过账口径不变。
ALTER TABLE inventory_inbound_item
    MODIFY COLUMN accepted_quantity DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '允收入库量（放行类行为 NULL：无质量判定）';

UPDATE inventory_inbound_item i
JOIN inventory_inbound_order o ON o.inbound_id = i.inbound_id
SET i.qualified_quantity = NULL,
    i.rejected_quantity = NULL,
    i.accepted_quantity = NULL
WHERE o.inbound_type IN ('IQC_RELEASE', 'IQC_REWORK')
   OR o.source_type IN ('IQC_RELEASE', 'IQC_REWORK');
