-- ============================================================================
-- 91_film_screen_link.sql
-- 菲林 → 网版 关联（dev-20260911-003）
--   背景：jjx_screen_master 是 Excel 导入的网版台账（4502 条），与产品/菲林零关联；
--         行业内菲林（设计图形）是网版（制版）的上游，需要能追溯「这张网版是哪张菲林制出来的」。
--   改动：仅新增 3 个可空列 + 2 个索引，历史导入数据不受影响（新列为 NULL 即"非菲林来源"）。
--   幂等：先查 information_schema 再决定是否 ALTER，可重复执行。
--   作者/任务：Hermes Agent / dev-20260911-003
-- ============================================================================

SET @db := DATABASE();

-- 1) jjx_screen_master.product_id
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'jjx_screen_master' AND COLUMN_NAME = 'product_id');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `jjx_screen_master` ADD COLUMN `product_id` BIGINT NULL COMMENT ''关联产品ID（由菲林生成时有值）''',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) jjx_screen_master.product_code
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'jjx_screen_master' AND COLUMN_NAME = 'product_code');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `jjx_screen_master` ADD COLUMN `product_code` VARCHAR(50) NULL COMMENT ''关联产品编码''',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) jjx_screen_master.film_id
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'jjx_screen_master' AND COLUMN_NAME = 'film_id');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `jjx_screen_master` ADD COLUMN `film_id` BIGINT NULL COMMENT ''来源菲林ID（engineering_film.film_id）''',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) 索引：按菲林 / 按产品 反查网版
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'jjx_screen_master' AND INDEX_NAME = 'idx_screen_film');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `jjx_screen_master` ADD INDEX `idx_screen_film` (`film_id`)',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'jjx_screen_master' AND INDEX_NAME = 'idx_screen_product');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `jjx_screen_master` ADD INDEX `idx_screen_product` (`product_id`)',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
