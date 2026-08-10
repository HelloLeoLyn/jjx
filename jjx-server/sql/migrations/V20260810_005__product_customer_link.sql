-- =====================================================
-- V20260810_005：产品关联客户
-- 产品表加 customer_id（客户外键）+ customer_name（冗余，列表/详情直接显示）
-- 2026-08-10：产品真正关联客户，编辑回显/按客户查询可靠
-- =====================================================

ALTER TABLE `product`
    ADD COLUMN `customer_id` BIGINT NULL COMMENT '客户ID（sales_customer.customer_id）' AFTER `category_id`,
    ADD COLUMN `customer_name` VARCHAR(200) NULL COMMENT '客户名称（冗余）' AFTER `customer_id`;

-- 历史数据回填：从产品编码前缀反推客户简称，匹配 sales_customer.customer_short_name
-- 注意：product_code 与 customer_short_name 排序规则不同（0900_ai_ci vs unicode_ci），需显式 COLLATE
UPDATE product p
    LEFT JOIN sales_customer c
        ON p.customer_name IS NULL
       AND c.customer_short_name IS NOT NULL
       AND c.customer_short_name != ''
       AND p.product_code LIKE CONCAT(c.customer_short_name COLLATE utf8mb4_unicode_ci, '%')
    SET p.customer_id = c.customer_id,
        p.customer_name = c.customer_name
WHERE p.customer_id IS NULL
  AND c.customer_id IS NOT NULL;
