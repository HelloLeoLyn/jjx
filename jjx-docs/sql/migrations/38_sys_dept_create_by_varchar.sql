-- dev-20260901 修复 sys_dept.create_by/update_by 类型：bigint → varchar(64)
-- 根因：全库其他表 create_by/update_by 均为 varchar(64)，sys_dept 建表误用 bigint，
--       新增部门时写入 'admin' 字符串报 Incorrect integer value
-- 幂等：类型仍为 bigint 才 ALTER；存量数字值（userId）转字符串兼容

SET @need_alter = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_dept'
      AND COLUMN_NAME = 'create_by'
      AND DATA_TYPE = 'bigint'
);

SET @stmt = IF(@need_alter > 0,
    'ALTER TABLE sys_dept MODIFY COLUMN create_by VARCHAR(64) NULL COMMENT ''创建者'', MODIFY COLUMN update_by VARCHAR(64) NULL COMMENT ''更新者''',
    'DO 0');

PREPARE alter_dept FROM @stmt;
EXECUTE alter_dept;
DEALLOCATE PREPARE alter_dept;
