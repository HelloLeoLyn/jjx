-- dev-20260930-009
-- uk_exec_first 只约束每个 execution 的 STANDARD 根任务。
-- SUPPLEMENT 根任务按补料出库单和工序由 uk_task_supplement_outbound_exec 去重。
-- 仅重定义生成列和索引，不更新或删除 production_task 业务行。

SET @has_uk_exec_first = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'production_task'
    AND index_name = 'uk_exec_first'
);
SET @drop_uk_exec_first = IF(
  @has_uk_exec_first > 0,
  'ALTER TABLE production_task DROP INDEX uk_exec_first',
  'SELECT 1'
);
PREPARE drop_uk_exec_first_stmt FROM @drop_uk_exec_first;
EXECUTE drop_uk_exec_first_stmt;
DEALLOCATE PREPARE drop_uk_exec_first_stmt;

ALTER TABLE production_task
  MODIFY COLUMN first_level_flag BIGINT
    GENERATED ALWAYS AS (IF(parent_task_id IS NULL AND task_type = 'STANDARD', 1, NULL)) STORED
    COMMENT '唯一标记：每个 execution 仅一个 STANDARD 根任务；SUPPLEMENT 根任务由补料出库单唯一键约束'
    AFTER task_type;

SET @has_uk_exec_first = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'production_task'
    AND index_name = 'uk_exec_first'
);
SET @add_uk_exec_first = IF(
  @has_uk_exec_first = 0,
  'ALTER TABLE production_task ADD UNIQUE KEY uk_exec_first (execution_id, first_level_flag)',
  'SELECT 1'
);
PREPARE add_uk_exec_first_stmt FROM @add_uk_exec_first;
EXECUTE add_uk_exec_first_stmt;
DEALLOCATE PREPARE add_uk_exec_first_stmt;
