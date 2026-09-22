-- ============================================================================
-- 175: 合同评审打印模板改为数据联动（dev-20260914-025）
-- 说明：开发库曾手工修正，补充幂等迁移供其他环境同步；本脚本不在此处执行。
-- ============================================================================
USE `jjx_erp_db`;

UPDATE `quality_template_registry`
SET `category` = 'data',
    `biz_type` = 'sales_order_review',
    `print_component` = 'views/sales/order/review-print.vue',
    `biz_module` = '销售管理-销售订单评审',
    `update_by` = 'migration-175',
    `update_time` = NOW()
WHERE `record_no` IN ('JJX-QR-047', 'JJX-QR-053')
  AND (
    `category` <> 'data'
    OR `biz_type` IS NULL OR `biz_type` <> 'sales_order_review'
    OR `print_component` IS NULL OR `print_component` <> 'views/sales/order/review-print.vue'
    OR `biz_module` IS NULL OR `biz_module` <> '销售管理-销售订单评审'
  );
