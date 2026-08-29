-- sys_oper_log.biz_status 由 int 改为 varchar(200)
-- 背景：@Log.bizStatus 是 SpEL 字符串容器，存的可能是数字码（"3"）也可能是语义值（"DRAFT"），
--       int 列迫使所有取值都必须能转成数字，改 varchar 后由各模块自行决定写什么。
-- 幂等：仅当当前类型仍为 int 时才 ALTER，重复执行跳过。
-- 存量数据：MySQL 将 int 自动转成十进制字符串（3 -> '3'），不需要额外回填。

SET @need_alter = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_oper_log'
      AND COLUMN_NAME = 'biz_status'
      AND DATA_TYPE = 'int'
);

SET @stmt = IF(@need_alter > 0,
    'ALTER TABLE sys_oper_log MODIFY COLUMN biz_status VARCHAR(200) NULL COMMENT ''业务状态值（各模块自定义，可为状态码或语义值）''',
    'DO 0');

PREPARE alter_biz_status FROM @stmt;
EXECUTE alter_biz_status;
DEALLOCATE PREPARE alter_biz_status;
