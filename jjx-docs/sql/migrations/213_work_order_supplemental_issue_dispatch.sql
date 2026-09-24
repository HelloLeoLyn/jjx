-- risk: low
-- dev-20260924-002：通用工单补料来源留痕，并支持同一工序下补产任务独立派工。
-- 仅新增补料原因/来源、补产任务分组字段与索引；库存仍只由既有出库流水驱动。

ALTER TABLE inventory_outbound_order
    ADD COLUMN supplement_reason_type VARCHAR(32) NULL COMMENT '补料来源类型：PRODUCTION_OVERUSE/SCRAP_REPLENISHMENT',
    ADD COLUMN supplement_reason VARCHAR(255) NULL COMMENT '补料原因说明',
    ADD COLUMN supplement_ncr_id BIGINT NULL COMMENT '报废补产关联质量不良单ID',
    ADD COLUMN supplement_production_quantity DECIMAL(14,2) NULL COMMENT '本次补料关联的补产成品数量';

ALTER TABLE inventory_outbound_order
    ADD KEY idx_outbound_supplement_ncr (supplement_ncr_id);

ALTER TABLE production_task
    ADD COLUMN task_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' COMMENT '任务类型：STANDARD/SUPPLEMENT',
    ADD COLUMN supplement_group_no VARCHAR(64) NULL COMMENT '补产批次组号，跨工序关联同一次补产',
    ADD COLUMN supplement_reason VARCHAR(255) NULL COMMENT '补产原因',
    ADD COLUMN source_ncr_id BIGINT NULL COMMENT '补产关联质量不良单ID',
    ADD COLUMN source_outbound_id BIGINT NULL COMMENT '补产所需的已发料补料单ID';

ALTER TABLE production_task
    ADD KEY idx_task_supplement_group (supplement_group_no),
    ADD UNIQUE KEY uk_task_supplement_outbound_exec (source_outbound_id, execution_id);
