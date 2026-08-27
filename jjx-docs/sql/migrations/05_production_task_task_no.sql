-- =====================================================
-- ProductionTask business number (Clean Slate)
-- 05_production_task_task_no.sql
--
-- 前置：开发库若已有 production_task 数据，请清理相关报工/任务/工序执行数据并重新生成，
-- 不在本迁移中保留或回填历史 Task 编号。
-- =====================================================

ALTER TABLE `production_operation_execution`
  ADD COLUMN `task_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '当前工序ProductionTask流水' AFTER `process_order`;

ALTER TABLE `production_task`
  ADD COLUMN `task_no` VARCHAR(96) NOT NULL COMMENT '任务号：{orderNo}-P{processOrder}-T{taskSeq}' AFTER `task_id`,
  ADD UNIQUE KEY `uk_production_task_task_no` (`task_no`);
