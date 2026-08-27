-- =====================================================
-- P1 Unified Production Task Foundation
-- 01_production_task_foundation.sql
--
-- 1. 新建 production_task（统一任务责任树：第一层与所有下级同构）
-- 2. production_work_report.task_node_id → task_id，并 FK 到 production_task
-- 3. 现存 OperationExecution 初始化 First Task（新模型事实初始化，非旧数据 backfill）
--
-- 原则：
--   - 无 System Root；parent_task_id = NULL 即第一层真实业务 Task
--   - completed/pending/assigned/remaining/has_children 等数量均为投影，不落库
--   - recalled_quantity 不保留；收回/退回通过条件扣减 task_quantity + 结构化流水表达（P2）
--   - 审批字段留 P3，本脚本不添加
-- =====================================================

CREATE TABLE IF NOT EXISTS `production_task` (
  `task_id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务ID（统一树节点ID，第一层与所有下级一致）',
  `execution_id`     BIGINT        NOT NULL                COMMENT '工序执行ID（工序上下文；FK production_operation_execution）',
  `parent_task_id`   BIGINT        NULL DEFAULT NULL       COMMENT '父任务ID；NULL=第一层真实任务（非 System Root）',
  `assignee_id`      BIGINT        NULL DEFAULT NULL       COMMENT '当前执行人（单值）；NULL=第一层未分配',
  `task_quantity`    DECIMAL(14,2) NOT NULL                COMMENT '本任务获得的有效任务总量（创建/分配时快照；收回时条件扣减）',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=未分配 / ACTIVE=已分配（P1 最小；完整状态机 P5）',
  `version`          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本（P2 分配/收回/退回并发地基）',
  `create_by`        VARCHAR(64)   DEFAULT NULL,
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`        VARCHAR(64)   DEFAULT NULL,
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `first_level_flag` BIGINT        GENERATED ALWAYS AS (IF(`parent_task_id` IS NULL, 1, NULL)) STORED COMMENT '生成列：第一层=1，子层=NULL；配合 uk_exec_first 保证每 execution 唯一 First Task',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_exec_first` (`execution_id`, `first_level_flag`),
  KEY `idx_parent_task` (`parent_task_id`),
  KEY `idx_assignee` (`assignee_id`),
  KEY `idx_execution` (`execution_id`),
  CONSTRAINT `fk_task_execution` FOREIGN KEY (`execution_id`)
    REFERENCES `production_operation_execution` (`execution_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_task_parent` FOREIGN KEY (`parent_task_id`)
    REFERENCES `production_task` (`task_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产任务（统一任务责任树）';

-- WorkReport 关联字段改名：task_node_id → task_id（旧表已删除；当前 0 行数据，安全）
ALTER TABLE `production_work_report`
  RENAME COLUMN `task_node_id` TO `task_id`,
  ADD CONSTRAINT `fk_work_report_task` FOREIGN KEY (`task_id`)
    REFERENCES `production_task` (`task_id`) ON DELETE RESTRICT;

-- 现存 OperationExecution 初始化 First Task（新模型事实初始化）
-- 每个 execution 一条：parent_task_id=NULL / assignee_id=NULL / task_quantity=input_quantity / status=PENDING
INSERT INTO `production_task`
  (`execution_id`, `parent_task_id`, `assignee_id`, `task_quantity`, `status`, `create_by`)
SELECT e.`execution_id`, NULL, NULL, e.`input_quantity`, 'PENDING', 'system'
FROM `production_operation_execution` e
WHERE NOT EXISTS (
  SELECT 1 FROM `production_task` t
  WHERE t.`execution_id` = e.`execution_id` AND t.`parent_task_id` IS NULL
);
