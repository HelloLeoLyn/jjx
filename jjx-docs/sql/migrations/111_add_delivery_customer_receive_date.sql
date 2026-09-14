-- dev-20260914-016 / 任务 1772（口径 D1）
-- 销售发货单：补「客户签收日期」（客户在纸质送货单上签字的日期，纸面日期）
-- 与 receive_time（系统登记时间）必须区分；可空，历史数据不回填。
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'sales_delivery'
             AND column_name = 'customer_receive_date');
SET @s := IF(@c = 0,
  'ALTER TABLE sales_delivery ADD COLUMN customer_receive_date DATE NULL COMMENT ''客户签收日期（纸面，口径D1）'' AFTER receive_time',
  'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
