-- dev-20261021-016：标准工序临时项（只交付脚本，未执行）。
-- risk: high
-- 执行前必须由用户手工做全库备份（排除 hr_employee），agent 不生成备份。
-- 用户备份后通过 scripts/db-migrate.sh 应用；不预设/授予任何角色。
-- 新列默认值使现有所有行成为 process_status=2、temp_flag=0；重跑不重置后续临时数据。

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND column_name = 'process_status'), 'SELECT 1', 'ALTER TABLE engineering_standard_process ADD COLUMN process_status TINYINT NOT NULL DEFAULT 2 COMMENT ''工序状态：1临时待审 2正式 3已驳回''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND column_name = 'temp_flag'), 'SELECT 1', 'ALTER TABLE engineering_standard_process ADD COLUMN temp_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''临时来源标记，转正后保留''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND column_name = 'reject_reason'), 'SELECT 1', 'ALTER TABLE engineering_standard_process ADD COLUMN reject_reason VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''驳回原因''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND column_name = 'approved_by'), 'SELECT 1', 'ALTER TABLE engineering_standard_process ADD COLUMN approved_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''审核人''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND column_name = 'approved_time'), 'SELECT 1', 'ALTER TABLE engineering_standard_process ADD COLUMN approved_time DATETIME DEFAULT NULL COMMENT ''审核时间''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'engineering_standard_process' AND index_name = 'idx_process_status_enabled_order'), 'SELECT 1', 'ALTER TABLE engineering_standard_process ADD INDEX idx_process_status_enabled_order (process_status, is_enabled, display_order)');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

INSERT INTO sys_menu (menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, create_by, create_time, update_by, remark)
SELECT '审核临时工序', p.menu_id, 10, 'F', '0', '0', 'product:standardProcess:approve', NULL, 'codex', NOW(), 'codex', 'dev-20261021-016；角色授权由用户在权限系统内配置'
FROM sys_menu p
WHERE p.component = 'views/product/standard-process/index.vue'
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms = 'product:standardProcess:approve');
