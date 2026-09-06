-- 单项 IQC 第一阶段：统一质量来源、复检链和入库明细检验/过账数量。
-- MySQL 8.x；上线前请先在测试库执行并备份。

ALTER TABLE production_quality_inspection
    ADD COLUMN source_type VARCHAR(32) NULL COMMENT '业务来源类型' AFTER inspection_type,
    ADD COLUMN source_id BIGINT NULL COMMENT '来源主单ID' AFTER source_type,
    ADD COLUMN source_item_id BIGINT NULL COMMENT '来源明细ID' AFTER source_id,
    ADD COLUMN batch_no VARCHAR(100) NULL COMMENT '检验批次' AFTER source_item_id,
    ADD COLUMN previous_inspection_id BIGINT NULL COMMENT '上一次检验ID' AFTER batch_no,
    ADD COLUMN inspection_version INT NOT NULL DEFAULT 1 COMMENT '检验版本' AFTER previous_inspection_id,
    ADD COLUMN disposition VARCHAR(32) NULL COMMENT '不合格处置' AFTER inspection_version,
    ADD COLUMN reviewer_id BIGINT NULL COMMENT '复核人ID' AFTER remark,
    ADD COLUMN reviewer_name VARCHAR(100) NULL COMMENT '复核人' AFTER reviewer_id,
    ADD COLUMN review_time DATETIME NULL COMMENT '复核时间' AFTER reviewer_name,
    ADD INDEX idx_quality_source (source_type, source_id, source_item_id),
    ADD INDEX idx_quality_previous (previous_inspection_id);

ALTER TABLE inventory_inbound_item
    ADD COLUMN sampled_quantity DECIMAL(18,4) NULL COMMENT '抽检数量' AFTER quantity,
    ADD COLUMN inspection_id BIGINT NULL COMMENT '当前IQC检验ID' AFTER sampled_quantity,
    ADD COLUMN inspection_result VARCHAR(20) NULL COMMENT '当前检验结论' AFTER inspection_id,
    ADD COLUMN disposition VARCHAR(32) NULL COMMENT '不合格处置' AFTER inspection_result,
    ADD COLUMN accepted_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '最终允收数量' AFTER rejected_quantity,
    ADD COLUMN posted_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已过账数量' AFTER accepted_quantity,
    ADD INDEX idx_inbound_item_inspection (inspection_id);

-- 旧数据兼容：已完成单据视为原数量已过账，防止升级后重复入库。
UPDATE inventory_inbound_item i
JOIN inventory_inbound_order o ON o.inbound_id = i.inbound_id
SET i.accepted_quantity = i.quantity,
    i.posted_quantity = i.quantity
WHERE o.order_status = 10;

ALTER TABLE production_quality_inspection_item
    ADD COLUMN inspection_method VARCHAR(255) NULL COMMENT '检验方法' AFTER standard,
    ADD COLUMN equipment VARCHAR(255) NULL COMMENT '检验设备' AFTER inspection_method,
    ADD COLUMN cr_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '致命缺陷数' AFTER result,
    ADD COLUMN ma_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '主要缺陷数' AFTER cr_quantity,
    ADD COLUMN mi_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '次要缺陷数' AFTER ma_quantity;
