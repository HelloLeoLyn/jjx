-- =====================================================
-- P2 Production Task Flow
-- 02_production_task_flow.sql
--
-- 1. 新建 production_task_event（业务流水：为什么变成现在这样）
--    - 树 = 当前状态（production_task）
--    - 流水 = 责任/数量变化历史（production_task_event）
--    - 完成事实 = WorkReport（生产事实，不进入本表）
-- 2. before_task_quantity / after_task_quantity 唯一语义：
--    记录 event.task_id 所代表 ProductionTask 的 task_quantity 动作前后值，
--    任何 action 都不得改变该含义（不混入 remaining）。
-- 3. 不新增 recalled_quantity / returned_quantity / original_quantity，
--    历史变化一律由 TaskEvent 还原。
--
-- 原则：
--   - action P2 最小：FIRST_ASSIGN / ASSIGN / RECALL / RETURN / UNASSIGN
--   - 无 System Root；不恢复任何旧 TaskNode/Dispatch 结构
--   - CANCELLED 最小状态由 Task 行表达（task_quantity=0 + status=CANCELLED），
--     物理记录永远保留，活动树默认不显示
-- =====================================================

CREATE TABLE IF NOT EXISTS `production_task_event` (
  `event_id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `task_id`              BIGINT        NOT NULL                COMMENT '动作主任务ID（FK production_task）',
  `related_task_id`      BIGINT        NULL DEFAULT NULL       COMMENT '关联任务ID（ASSIGN=新child / RECALL=被收回child / RETURN=父任务；FIRST_ASSIGN/UNASSIGN=NULL）',
  `action`               VARCHAR(20)   NOT NULL                COMMENT 'FIRST_ASSIGN/ASSIGN/RECALL/RETURN/UNASSIGN',
  `operator_id`          BIGINT        NOT NULL                COMMENT '操作人ID',
  `operator_name`        VARCHAR(64)   NULL DEFAULT NULL       COMMENT '操作人姓名',
  `from_assignee_id`     BIGINT        NULL DEFAULT NULL       COMMENT '动作前执行人ID',
  `to_assignee_id`       BIGINT        NULL DEFAULT NULL       COMMENT '动作后执行人ID',
  `quantity`             DECIMAL(14,2) NOT NULL                COMMENT '本次流转数量',
  `before_task_quantity` DECIMAL(14,2) NOT NULL                COMMENT 'event.task_id 的 task_quantity 动作前值',
  `after_task_quantity`  DECIMAL(14,2) NOT NULL                COMMENT 'event.task_id 的 task_quantity 动作后值',
  `remark`               VARCHAR(500)  NULL DEFAULT NULL       COMMENT '备注',
  `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by`            VARCHAR(64)   NULL DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_event_task` (`task_id`),
  KEY `idx_event_related` (`related_task_id`),
  KEY `idx_event_action` (`action`),
  CONSTRAINT `fk_event_task` FOREIGN KEY (`task_id`)
    REFERENCES `production_task` (`task_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产任务流转事件（业务流水，非操作审计；树=当前状态，流水=为什么变成现在这样）';
