-- dev-20261009-007
-- risk: low（仅对既有表加列，不改/删任何既有数据）
-- 内容：sales_quotation 增加 shipping_fee 列（报价单运费，单列不计税），对齐销售订单 sales_order.shipping_fee 口径。
-- 幂等：加列前查 information_schema.columns，列已存在则跳过。
-- 执行：bash scripts/db-migrate.sh 248_sales_quotation_shipping_fee.sql --yes --task dev-20261009-007
-- 背景：报价单此前无运费字段，而销售订单（dev-20261008-002 起）已有 shipping_fee；
--       报价是对外口径，成交收运费时「报价最终金额 ≠ 转单后应付」易扯皮。
--       本次补齐后金额口径（对齐销售订单）：total_amount = 未税小计 + 税额 + 运费；final_amount = total_amount - 折扣。

SET @jjx_has_qsf = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sales_quotation' AND column_name = 'shipping_fee'
);
SET @jjx_ddl_qsf = IF(@jjx_has_qsf = 0,
'ALTER TABLE sales_quotation ADD COLUMN shipping_fee DECIMAL(15,2) NULL DEFAULT NULL COMMENT ''运费（单列，不计税）'' AFTER total_amount',
'SELECT 1');
PREPARE jjx_ddl_qsf FROM @jjx_ddl_qsf;
EXECUTE jjx_ddl_qsf;
DEALLOCATE PREPARE jjx_ddl_qsf;
