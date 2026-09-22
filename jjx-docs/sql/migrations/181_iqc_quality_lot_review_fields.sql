-- dev-20260921-035：IQC 读写切换到 quality_lot，补齐独立审核事实。
SET @db := DATABASE();
SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE quality_lot ADD COLUMN review_status VARCHAR(20) NULL COMMENT ''DRAFT/PENDING/APPROVED/REJECTED'' AFTER status',
  'SELECT 1') FROM information_schema.columns WHERE table_schema=@db AND table_name='quality_lot' AND column_name='review_status');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(COUNT(*)=0,
  'ALTER TABLE quality_lot ADD COLUMN reviewer_id BIGINT NULL AFTER inspector, ADD COLUMN reviewer_name VARCHAR(64) NULL AFTER reviewer_id, ADD COLUMN review_time DATETIME NULL AFTER reviewer_name, ADD COLUMN review_remark VARCHAR(500) NULL AFTER review_time, ADD COLUMN defect_reason VARCHAR(500) NULL AFTER review_remark',
  'SELECT 1') FROM information_schema.columns WHERE table_schema=@db AND table_name='quality_lot' AND column_name='reviewer_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
