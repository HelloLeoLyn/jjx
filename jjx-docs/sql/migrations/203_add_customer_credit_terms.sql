-- dev-20260921-045 客户账期结构化；存量付款方式按既有定义迁移。
ALTER TABLE sales_customer
    ADD COLUMN payment_term_type VARCHAR(20) NULL COMMENT '账期类型：PREPAID/COD/NET_DAYS/MONTH_END' AFTER payment_terms,
    ADD COLUMN credit_days INT NULL COMMENT '账期天数' AFTER payment_term_type,
    ADD COLUMN credit_start_basis VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER_RECEIPT_DATE' COMMENT '账期起算基准' AFTER credit_days;

UPDATE sales_customer
SET payment_term_type = CASE payment_method
        WHEN 1 THEN 'PREPAID'
        WHEN 2 THEN 'COD'
        WHEN 3 THEN 'MONTH_END'
        WHEN 4 THEN 'MONTH_END'
        ELSE NULL
    END,
    credit_days = CASE payment_method
        WHEN 1 THEN 0
        WHEN 2 THEN 0
        WHEN 3 THEN 30
        WHEN 4 THEN 60
        ELSE NULL
    END
WHERE payment_term_type IS NULL;
