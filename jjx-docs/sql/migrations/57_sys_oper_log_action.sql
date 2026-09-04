-- sys_oper_log 增加操作动作中文文案快照。
-- 幂等：仅当 action 列不存在时执行 ALTER，重复执行跳过。

SET @need_alter = (
    SELECT COUNT(*) = 0
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_oper_log'
      AND COLUMN_NAME = 'action'
);

SET @stmt = IF(@need_alter > 0,
    'ALTER TABLE sys_oper_log ADD COLUMN action VARCHAR(100) NULL COMMENT ''操作动作中文文案（如 执行预警检查）''',
    'DO 0');

PREPARE alter_sys_oper_log_action FROM @stmt;
EXECUTE alter_sys_oper_log_action;
DEALLOCATE PREPARE alter_sys_oper_log_action;
