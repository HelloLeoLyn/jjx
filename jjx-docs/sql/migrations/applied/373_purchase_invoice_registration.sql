-- 任务：dev-20261117-036；risk: low
-- 采购发票登记与人工核验：票面原始事实及审计字段，复用purchase_document，不新增业务表。
-- 票面价税合计复用document_amount，票面日期复用document_date；不存订单派生统计。
-- 编号依据：目标库ops.schema.applied最大372，目录最大372（2026-11-17核对）。
-- 历史值不猜测、不回填；原默认日期/零金额在票面未补录时不作为已核实信息展示。
-- 金额精度DECIMAL(12,2)，和现有document_amount一致；字符串统一utf8mb4_unicode_ci。
-- 执行须经scripts/db-migrate.sh，用户手工备份排除hr_employee；无数据UPDATE/DELETE。

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='invoice_no'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN invoice_no VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''真实发票号码，系统登记编号仍为document_no''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='seller_name'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN seller_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''票面销售方名称''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='seller_tax_no'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN seller_tax_no VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''票面销售方税号''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='buyer_name'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN buyer_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''票面购买方名称''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='buyer_tax_no'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN buyer_tax_no VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''票面购买方税号''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='untaxed_amount'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN untaxed_amount DECIMAL(12,2) NULL COMMENT ''票面不含税金额，非订单派生值''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='tax_amount'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN tax_amount DECIMAL(12,2) NULL COMMENT ''票面税额，非订单派生值''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='verified_by'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN verified_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''实际核验登录用户ID''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='verifier_name'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN verifier_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''实际核验人显示姓名快照''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='verified_time'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN verified_time DATETIME NULL COMMENT ''服务器实际核验时间''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_document' AND column_name='verification_remark'), 'SELECT 1', 'ALTER TABLE purchase_document ADD COLUMN verification_remark VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''核验意见，与登记备注分开''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='purchase_document' AND index_name='uk_purchase_invoice_no'), 'SELECT 1', 'ALTER TABLE purchase_document ADD UNIQUE INDEX uk_purchase_invoice_no (invoice_no)');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
