-- ============================================================================
-- 174_add_missing_audit_columns.sql
-- 任务码：dev-20260914-024
--
-- 背景：下列实体继承或声明了通用审计字段，但历史表结构缺少 update_by / update_time，
-- MyBatis-Plus 默认查询会把实体字段拼入 SELECT，导致退货收货等链路报 Unknown column。
--
-- 风险：表结构变更。执行前必须通过 scripts/db-migrate.sh 完成全库备份；本文件仅定义迁移，
-- 本次代码实施不直接执行数据库变更。
--
-- 幂等：MySQL 8 不统一支持 ADD COLUMN IF NOT EXISTS，因此逐列查询
-- information_schema.columns，不存在时才执行 ALTER TABLE。
-- ============================================================================

SET @db := DATABASE();

-- sales_return_item
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sales_return_item' AND column_name='update_by') = 0,
  'ALTER TABLE sales_return_item ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_time',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sales_return_item' AND column_name='update_time') = 0,
  'ALTER TABLE sales_return_item ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- review_flow
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='review_flow' AND column_name='update_by') = 0,
  'ALTER TABLE review_flow ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_time',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='review_flow' AND column_name='update_time') = 0,
  'ALTER TABLE review_flow ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- production_task_event
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='production_task_event' AND column_name='update_by') = 0,
  'ALTER TABLE production_task_event ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='production_task_event' AND column_name='update_time') = 0,
  'ALTER TABLE production_task_event ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- production_trace_log
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='production_trace_log' AND column_name='update_by') = 0,
  'ALTER TABLE production_trace_log ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_time',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='production_trace_log' AND column_name='update_time') = 0,
  'ALTER TABLE production_trace_log ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sales_sample_bom
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sales_sample_bom' AND column_name='update_by') = 0,
  'ALTER TABLE sales_sample_bom ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_time',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sales_sample_bom' AND column_name='update_time') = 0,
  'ALTER TABLE sales_sample_bom ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sales_sample_transfer
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sales_sample_transfer' AND column_name='update_by') = 0,
  'ALTER TABLE sales_sample_transfer ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_time',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sales_sample_transfer' AND column_name='update_time') = 0,
  'ALTER TABLE sales_sample_transfer ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- engineering_resource_product_rel 已有 update_time，只补 update_by。
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='engineering_resource_product_rel' AND column_name='update_by') = 0,
  'ALTER TABLE engineering_resource_product_rel ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_tag_rel
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sys_tag_rel' AND column_name='update_by') = 0,
  'ALTER TABLE sys_tag_rel ADD COLUMN update_by varchar(64) DEFAULT NULL COMMENT ''更新者'' AFTER create_time',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='sys_tag_rel' AND column_name='update_time') = 0,
  'ALTER TABLE sys_tag_rel ADD COLUMN update_time datetime DEFAULT NULL COMMENT ''更新时间'' AFTER update_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 执行后核验（预期 8 张表均为 2）：
-- SELECT table_name, COUNT(*) AS audit_columns
-- FROM information_schema.columns
-- WHERE table_schema = DATABASE()
--   AND table_name IN ('sales_return_item','review_flow','production_task_event',
--     'production_trace_log','sales_sample_bom','sales_sample_transfer',
--     'engineering_resource_product_rel','sys_tag_rel')
--   AND column_name IN ('update_by','update_time')
-- GROUP BY table_name ORDER BY table_name;
