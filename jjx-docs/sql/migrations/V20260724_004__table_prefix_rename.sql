-- ============================================================
-- Migration: V20260724_004__table_prefix_rename.sql
-- 重命名 product_* 表为 engineering_*
-- 注意：执行前请确保 product 包不再引用旧表名
-- Applied: 2026-07-24
-- ============================================================

-- 备份外键信息
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

RENAME TABLE
  product_bom TO engineering_bom,
  product_bom_item TO engineering_bom_item,
  product_config_model TO engineering_config_model,
  product_config_option TO engineering_config_option,
  product_film TO engineering_film,
  product_routing TO engineering_routing,
  product_routing_item TO engineering_routing_item,
  product_standard_process TO engineering_standard_process;

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

SELECT '✅ 表前缀改名完成' AS result;
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'engineering_%'
ORDER BY TABLE_NAME;
