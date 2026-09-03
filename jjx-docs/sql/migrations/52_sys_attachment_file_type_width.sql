-- 加宽 sys_attachment.file_type，继续用于存储上传文件的 MIME 类型（getContentType）。
-- 背景：Office 新格式 MIME 可能超过原 varchar(50)，例如
--       application/vnd.openxmlformats-officedocument.spreadsheetml.sheet 长 62 字符，上传时会报 Data too long。
-- 行为不变：仅将列宽扩大到 varchar(255)，不改变 file_type 的写入语义。
-- 幂等：仅当列存在且当前 CHARACTER_MAXIMUM_LENGTH < 255 时才 ALTER，重复执行跳过。

SET @need_alter = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_attachment'
      AND COLUMN_NAME = 'file_type'
      AND CHARACTER_MAXIMUM_LENGTH < 255
);

SET @stmt = IF(@need_alter > 0,
    'ALTER TABLE sys_attachment MODIFY COLUMN file_type VARCHAR(255) NULL DEFAULT '''' COMMENT ''MIME类型''',
    'DO 0');

PREPARE alter_attachment_file_type FROM @stmt;
EXECUTE alter_attachment_file_type;
DEALLOCATE PREPARE alter_attachment_file_type;
