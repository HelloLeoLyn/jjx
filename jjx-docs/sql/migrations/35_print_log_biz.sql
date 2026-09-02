-- dev-20260901-1237 打印日志增加业务单据维度
-- 幂等：分别判断字段是否存在，仅补充缺失字段。

SET @add_biz_type = (
    SELECT COUNT(*) = 0
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quality_template_print_log'
      AND COLUMN_NAME = 'biz_type'
);

SET @stmt = IF(@add_biz_type,
    'ALTER TABLE quality_template_print_log ADD COLUMN biz_type VARCHAR(50) NULL COMMENT ''业务类型（如 sales_delivery）'' AFTER record_no',
    'DO 0');
PREPARE add_print_log_biz_type FROM @stmt;
EXECUTE add_print_log_biz_type;
DEALLOCATE PREPARE add_print_log_biz_type;

SET @add_biz_id = (
    SELECT COUNT(*) = 0
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quality_template_print_log'
      AND COLUMN_NAME = 'biz_id'
);

SET @stmt = IF(@add_biz_id,
    'ALTER TABLE quality_template_print_log ADD COLUMN biz_id BIGINT NULL COMMENT ''业务单据ID'' AFTER biz_type',
    'DO 0');
PREPARE add_print_log_biz_id FROM @stmt;
EXECUTE add_print_log_biz_id;
DEALLOCATE PREPARE add_print_log_biz_id;
