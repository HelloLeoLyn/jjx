-- dev-20261110-007：库存预警三段闭环；幂等加列、回填与状态收敛。
SET @alert_column_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'inventory_alert_log' AND column_name = 'related_order_no');
SET @alert_ddl = IF(@alert_column_exists = 0,
    'ALTER TABLE inventory_alert_log ADD COLUMN related_order_no varchar(50) NULL COMMENT ''关联采购订单号（dev-20261110-007）''',
    'SELECT 1');
PREPARE alert_stmt FROM @alert_ddl;
EXECUTE alert_stmt;
DEALLOCATE PREPARE alert_stmt;

-- 单号可能接中文分号，不能把后续备注一并当成单号。
UPDATE inventory_alert_log
SET related_order_no = REGEXP_SUBSTR(
    SUBSTRING(process_remark, LOCATE('生成采购订单 ', process_remark) + CHAR_LENGTH('生成采购订单 ')),
    '^PO-[^[:space:];；，,]+')
WHERE (related_order_no IS NULL OR related_order_no = '')
  AND process_remark REGEXP '生成采购订单 PO-[^[:space:];；，,]+';

UPDATE inventory_alert_log
SET status = 0,
    process_remark = CONCAT_WS('；', NULLIF(process_remark, ''), '历史已上报状态归并为未处理')
WHERE status = 1;
