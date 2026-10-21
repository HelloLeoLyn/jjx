-- dev-20261020-029：BOM 快速模式 + 行级审核（草稿/待审核/审核通过/审核驳回/待移除）。
-- 仅新增可空/带默认值字段，不重建表、不改历史业务数据；不执行，由用户在备份后走 db-migrate.sh 应用。
-- risk: medium（新增列 + 角色授权；无数据删除）
SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'review_status'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `review_status` TINYINT NOT NULL DEFAULT 1 COMMENT ''行审核状态：1草稿 2待审核 3审核通过 4审核驳回 5待移除'' AFTER material_id');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'reject_reason'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `reject_reason` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''行审核驳回原因'' AFTER review_status');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'approved_fingerprint'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `approved_fingerprint` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT ''批准时内容指纹（SHA-256）'' AFTER reject_reason');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'approved_by'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `approved_by` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''行审核通过人'' AFTER approved_fingerprint');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'approved_time'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `approved_time` DATETIME DEFAULT NULL COMMENT ''行审核通过时间'' AFTER approved_by');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'remove_confirmed'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `remove_confirmed` TINYINT NOT NULL DEFAULT 0 COMMENT ''待移除行审核员已确认：0否 1是'' AFTER approved_time');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'remove_confirmed_by'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `remove_confirmed_by` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''移除确认人'' AFTER remove_confirmed');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'engineering_bom_item' AND column_name = 'remove_confirmed_time'),
  'SELECT 1',
  'ALTER TABLE engineering_bom_item ADD COLUMN `remove_confirmed_time` DATETIME DEFAULT NULL COMMENT ''移除确认时间'' AFTER remove_confirmed_by');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 历史数据：已批准 BOM 的明细视为审核通过；其余保持草稿。指纹留空，保存时按“未变更的已通过行”收养。
UPDATE engineering_bom_item i
  JOIN engineering_bom b ON b.bom_id = i.bom_id
   SET i.review_status = 3
 WHERE b.approve_status = 3
   AND i.review_status = 1;

-- 审核员可在审核页给未建档材料建档：给已有 BOM 审核权限的角色补 inventory:material:add（幂等）。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 110
  FROM sys_role r
 WHERE r.del_flag = '0'
   AND EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 209)
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm2 WHERE rm2.role_id = r.role_id AND rm2.menu_id = 110);
