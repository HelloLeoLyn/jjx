-- risk: low
-- dev-20261009-029：复用 sys_attachment 按图纸编号、版本关联原稿与打印件，不增加业务表。
-- 执行前由用户按 CONVENTIONS §2 手工备份；本任务只提供脚本，不自动备份或执行迁移。
-- 旧附件不回填、不按文件名猜测图纸关系；在工程图纸页面人工补录。
SET @db := DATABASE();
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='drawing_no')=0,
  'ALTER TABLE sys_attachment ADD COLUMN drawing_no VARCHAR(80) NULL COMMENT ''图纸编号（同产品内唯一标识一张图纸）'' AFTER version','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='drawing_name')=0,
  'ALTER TABLE sys_attachment ADD COLUMN drawing_name VARCHAR(120) NULL COMMENT ''图纸名称'' AFTER drawing_no','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='file_role')=0,
  'ALTER TABLE sys_attachment ADD COLUMN file_role VARCHAR(20) NULL COMMENT ''ORIGINAL工程原稿/PRINT预览打印件'' AFTER drawing_name','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND INDEX_NAME='idx_attachment_drawing')=0,
  'ALTER TABLE sys_attachment ADD INDEX idx_attachment_drawing (biz_type,biz_id,drawing_no,version,deleted)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_attachment' AND COLUMN_NAME IN ('drawing_no','drawing_name','file_role');
