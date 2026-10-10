-- risk: high
-- dev-20261010-036 样品单来源改为类型 + 单号；普通销售订单继续使用 quotation_id。
-- 执行前按 CONVENTIONS §2 由用户手工备份，且先应用前序迁移。

ALTER TABLE sales_sample_order
    ADD COLUMN source_type VARCHAR(20) NULL COMMENT '来源类型：QUOTATION/SALES_ORDER/SAMPLE_ORDER' AFTER order_id,
    ADD COLUMN source_no VARCHAR(80) NULL COMMENT '来源单号快照' AFTER source_type;

UPDATE sales_sample_order s
JOIN sales_order o ON o.order_id = s.order_id AND o.order_type = 2
JOIN sales_quotation q ON q.quotation_id = o.quotation_id
SET s.source_type = 'QUOTATION', s.source_no = q.quotation_no
WHERE o.quotation_id IS NOT NULL AND s.source_type IS NULL;

-- 仅清除已成功回填的样品单报价关联，避免孤儿报价记录丢失来源线索。
UPDATE sales_order o
JOIN sales_sample_order s ON s.order_id = o.order_id
SET o.quotation_id = NULL
WHERE o.order_type = 2 AND o.quotation_id IS NOT NULL
  AND s.source_type = 'QUOTATION' AND s.source_no IS NOT NULL;
