-- task: dev-20261014-039
-- 历史业务数据已由用户清理；只增加引用快照列，不回填、不删除旧列。
-- 执行前通过 db-migrate.sh 校验用户手工全库备份。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='sales_quotation_flow' AND column_name='attachment_ref_ids'),
    'SELECT 1', 'ALTER TABLE sales_quotation_flow ADD COLUMN attachment_ref_ids JSON NULL COMMENT ''本次流转的附件引用ID快照'' AFTER attachment_ids');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='sales_sample_round' AND column_name='attachment_ref_ids'),
    'SELECT 1', 'ALTER TABLE sales_sample_round ADD COLUMN attachment_ref_ids JSON NULL COMMENT ''本轮图纸附件引用ID快照'' AFTER attachment_ids');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
