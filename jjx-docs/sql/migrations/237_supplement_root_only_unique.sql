-- dev-20260930-016
-- risk: low
-- 补产（SUPPLEMENT）唯一键只约束「根」任务：派工子任务（parent_task_id 非空）不再占槽位。
-- 背景（dev-20260930-012）：uk_task_supplement_outbound_exec = (source_outbound_id, execution_id)
--   只允许一对一格任务，而派工 assign() 会把父任务的 source_outbound_id 原样抄给子任务
--   （ProductionTaskServiceImpl:827）→ 补产任务一派人就 `Duplicate entry '2-1'`。
-- 措施：加生成列 supplement_root_flag（根=1、子=NULL，与 first_level_flag 同构），唯一键改三列；
--   配合同提交的代码改动「补产幂等查询只找根任务」（ProductionTaskServiceImpl 两处）。
-- 影响：仅重定义生成列与索引；不更新、不删除任何 production_task 业务行。

-- 前置自检（应返回 0 行）：同一 (source_outbound_id, execution_id) 已有 2 个及以上「根」任务
SELECT source_outbound_id, execution_id, COUNT(*) AS roots
  FROM production_task
 WHERE source_outbound_id IS NOT NULL
   AND parent_task_id IS NULL
 GROUP BY source_outbound_id, execution_id
HAVING COUNT(*) > 1;

-- 1) 加生成列（幂等：已存在则跳过）
SET @has_col = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'production_task'
     AND column_name = 'supplement_root_flag'
);
SET @add_col = IF(
  @has_col = 0,
  'ALTER TABLE production_task ADD COLUMN supplement_root_flag BIGINT GENERATED ALWAYS AS (IF(parent_task_id IS NULL, 1, NULL)) STORED COMMENT ''唯一标记：每张补料出库单+工序仅一个补产根任务；派工子任务为 NULL'' AFTER first_level_flag',
  'SELECT 1'
);
PREPARE add_supplement_root_flag FROM @add_col;
EXECUTE add_supplement_root_flag;
DEALLOCATE PREPARE add_supplement_root_flag;

-- 2) 唯一键改三列（幂等：有则先删）
SET @has_uk = (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'production_task'
     AND index_name = 'uk_task_supplement_outbound_exec'
);
SET @drop_uk = IF(
  @has_uk > 0,
  'ALTER TABLE production_task DROP INDEX uk_task_supplement_outbound_exec',
  'SELECT 1'
);
PREPARE drop_supplement_uk FROM @drop_uk;
EXECUTE drop_supplement_uk;
DEALLOCATE PREPARE drop_supplement_uk;

ALTER TABLE production_task
  ADD UNIQUE KEY uk_task_supplement_outbound_exec (source_outbound_id, execution_id, supplement_root_flag);

-- 3) 收尾自检（应返回 1）：键列里含 supplement_root_flag
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name = 'production_task'
   AND index_name = 'uk_task_supplement_outbound_exec'
 GROUP BY index_name;
