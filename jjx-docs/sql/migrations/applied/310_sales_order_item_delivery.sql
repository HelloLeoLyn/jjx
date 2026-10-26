-- dev-20261026-003：报价交期继承到销售订单产品行。
-- risk: low；只新增两个可空列，不更新历史数据，不按报价有效期补交期。
-- 用户已确认按建议实施；经 db-migrate.sh 校验手工备份（排除 hr_employee）后应用。
-- 回退应先退回配套代码；删除新增列会丢失上线后的交期快照，须人工审核，不自动执行。
SELECT DATABASE() AS target_schema;

SET @delivery_days_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sales_order_product' AND column_name='delivery_days'),
  'SELECT ''delivery_days already exists'' AS migration_info',
  'ALTER TABLE sales_order_product ADD COLUMN delivery_days INT NULL COMMENT ''交期天数，历史空值不推断'''
);
PREPARE delivery_days_stmt FROM @delivery_days_sql;
EXECUTE delivery_days_stmt;
DEALLOCATE PREPARE delivery_days_stmt;

SET @estimated_delivery_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sales_order_product' AND column_name='estimated_delivery_date'),
  'SELECT ''estimated_delivery_date already exists'' AS migration_info',
  'ALTER TABLE sales_order_product ADD COLUMN estimated_delivery_date DATE NULL COMMENT ''预计交期，订单产品行保存快照'''
);
PREPARE estimated_delivery_stmt FROM @estimated_delivery_sql;
EXECUTE estimated_delivery_stmt;
DEALLOCATE PREPARE estimated_delivery_stmt;

SELECT column_name,data_type,is_nullable FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='sales_order_product'
 AND column_name IN ('delivery_days','estimated_delivery_date');
