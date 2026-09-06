-- 单项 IQC 第二阶段：检验结论与库存过账解耦。
-- MySQL 8.x；执行前请备份生产数据库。
ALTER TABLE production_quality_inspection
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态:DRAFT/PENDING/APPROVED/REJECTED' AFTER review_time,
    ADD COLUMN review_remark VARCHAR(500) NULL COMMENT '审核意见' AFTER review_status,
    ADD INDEX idx_quality_review_status (review_status);

-- 兼容第一阶段已形成的 IQC 记录。
UPDATE production_quality_inspection
SET review_status = CASE
    WHEN result IN ('pass', 'fail') THEN 'APPROVED'
    ELSE 'PENDING'
END
WHERE source_type = 'INBOUND';
