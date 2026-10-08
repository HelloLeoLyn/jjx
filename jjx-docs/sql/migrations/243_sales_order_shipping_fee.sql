-- dev-20261008-002
-- risk: low
-- 用户手工备份后执行；不自动执行，不回填历史运费。
-- shipping_fee NULL 表示旧单金额组成未确认；0 表示确认无运费。
-- 部署后端前必须执行本迁移，MyBatis-Plus 查询会读取新列。
SET @jjx_has_shipping_fee = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sales_order' AND column_name = 'shipping_fee'
);
SET @jjx_shipping_fee_ddl = IF(@jjx_has_shipping_fee = 0,
  'ALTER TABLE sales_order ADD COLUMN shipping_fee DECIMAL(15,2) NULL DEFAULT NULL COMMENT ''运费；NULL表示历史金额组成未确认'' AFTER total_amount',
  'SELECT 1');
PREPARE jjx_add_shipping_fee FROM @jjx_shipping_fee_ddl;
EXECUTE jjx_add_shipping_fee;
DEALLOCATE PREPARE jjx_add_shipping_fee;
