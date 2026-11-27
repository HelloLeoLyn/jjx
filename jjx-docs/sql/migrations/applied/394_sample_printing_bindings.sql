-- risk: low
-- P09 dev-20261125-015：复用打样工序、轮次和转移表，不建第二套方案表，不回填历史。
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_process' AND COLUMN_NAME='artwork_version_id')=0,'ALTER TABLE sales_sample_process ADD COLUMN artwork_version_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_process' AND COLUMN_NAME='default_plate_id')=0,'ALTER TABLE sales_sample_process ADD COLUMN default_plate_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_process' AND COLUMN_NAME='print_requirements')=0,'ALTER TABLE sales_sample_process ADD COLUMN print_requirements JSON NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_process' AND COLUMN_NAME='actual_plate_id')=0,'ALTER TABLE sales_sample_process ADD COLUMN actual_plate_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_process' AND COLUMN_NAME='execution_snapshot')=0,'ALTER TABLE sales_sample_process ADD COLUMN execution_snapshot JSON NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_transfer' AND COLUMN_NAME='source_round_id')=0,'ALTER TABLE sales_sample_transfer ADD COLUMN source_round_id BIGINT NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_transfer' AND COLUMN_NAME='request_id')=0,'ALTER TABLE sales_sample_transfer ADD COLUMN request_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_transfer' AND COLUMN_NAME='request_hash')=0,'ALTER TABLE sales_sample_transfer ADD COLUMN request_hash CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_transfer' AND INDEX_NAME='uk_sample_transfer_round')=0,'ALTER TABLE sales_sample_transfer ADD UNIQUE INDEX uk_sample_transfer_round(order_id,source_round_id)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_transfer' AND INDEX_NAME='uk_sample_transfer_request')=0,'ALTER TABLE sales_sample_transfer ADD UNIQUE INDEX uk_sample_transfer_request(request_id)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sales_sample_process' AND INDEX_NAME='idx_sample_actual_plate_status')=0,'ALTER TABLE sales_sample_process ADD INDEX idx_sample_actual_plate_status(actual_plate_id,status)','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
