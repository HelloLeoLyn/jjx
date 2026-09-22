-- ============================================================================
-- 177: 打印模板二维码配置（dev-20260916-007）
-- 口径：台账控制是否显示；当前使用 plain 纯业务单号。
-- 原因：现有 /m/scan 只支持生产工单，通用业务详情解析器尚不存在，禁止生成无效链接。
-- 本脚本只生成，不在当前批次直接执行。
-- ============================================================================
USE `jjx_erp_db`;

SET @schema_name = DATABASE();
SET @sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='quality_template_registry' AND column_name='qr_enabled'),
  'SELECT 1',
  'ALTER TABLE quality_template_registry ADD COLUMN qr_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否打印二维码'' AFTER print_mode'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='quality_template_registry' AND column_name='qr_content_rule'),
  'SELECT 1',
  'ALTER TABLE quality_template_registry ADD COLUMN qr_content_rule VARCHAR(20) NOT NULL DEFAULT ''link'' COMMENT ''二维码内容：link/plain'' AFTER qr_enabled'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE quality_template_registry
SET qr_enabled = 1, qr_content_rule = 'plain', update_by = 'migration-177', update_time = NOW()
WHERE record_no IN ('JJX-QR-024', 'JJX-QR-026', 'JJX-QR-047', 'JJX-QR-053', 'JJX-QR-065');
