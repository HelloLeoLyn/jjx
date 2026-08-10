-- ============================================================
-- Migration: V20260810_002__bom_item_applied_issue.sql
-- BOM 应用料/实际投料字段
-- 1) engineering_bom_item 加 applied_qty（应用料，含损耗）、actual_issue_qty（实际投料，按最低投料向上取整）
-- 幂等：IF NOT EXISTS 判断，可重复执行
-- ============================================================

-- 应用料：quantity × (1 + loss_rate/100)
ALTER TABLE engineering_bom_item
    ADD COLUMN applied_qty DECIMAL(14,4) NULL COMMENT '应用料（含损耗）= 用量×(1+损耗率/100)' AFTER quantity;

-- 实际投料：板材/卷材(material_type=R 且 min_issue_qty>0)时 = CEIL(applied/min_issue)×min_issue，否则=applied
ALTER TABLE engineering_bom_item
    ADD COLUMN actual_issue_qty DECIMAL(14,4) NULL COMMENT '实际投料（按最低投料向上取整）' AFTER applied_qty;
