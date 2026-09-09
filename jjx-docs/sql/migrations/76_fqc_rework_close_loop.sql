-- dev-20260909-011：FQC 分批检验、不良余量、返工执行闭环（0 新表）。

ALTER TABLE production_quality_inspection
    ADD COLUMN remaining_fail_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '待处置不良余量' AFTER fail_qty;

ALTER TABLE production_operation_execution
    ADD COLUMN execution_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '执行类型：NORMAL/REWORK' AFTER execution_id,
    ADD COLUMN source_inspection_id BIGINT NULL COMMENT '返工来源FQC检验ID' AFTER execution_type,
    ADD KEY idx_execution_source_inspection (source_inspection_id),
    ADD CONSTRAINT fk_execution_source_inspection FOREIGN KEY (source_inspection_id)
        REFERENCES production_quality_inspection (inspection_id) ON DELETE RESTRICT;

UPDATE production_quality_inspection
SET remaining_fail_qty = COALESCE(fail_qty, 0)
WHERE inspection_type='FQC' AND result='fail';
