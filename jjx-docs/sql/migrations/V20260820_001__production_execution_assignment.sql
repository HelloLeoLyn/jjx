-- ============================================================
-- V20260820_001__production_execution_assignment.sql
-- WP-B: 工序作业分配（ExecutionAssignment）—— 回答"哪个执行人做多少"
-- ------------------------------------------------------------
-- 领域语义（WP-A 定稿）:
--   Execution        = 一道工序整体生产任务（计划数量=input_quantity）
--   DispatchNode     = 责任链（当前谁负责，同一 dispatch 仅一个 ACTIVE）
--   ExecutionAssignment = 作业数量分配（谁做多少，可多个 ACTIVE 并存）
--   WorkReport       = 针对 Assignment 的实际报工事实
-- Assignment 不是第二套 DispatchNode，不表达责任转移，只表达数量份额。
--
-- 数量口径（Projection/计算，不存 reported/remaining 事实字段）:
--   assigned_quantity  创建后不可直接修改
--   released_quantity  释放剩余数量（默认 0）
--   effective_quantity = assigned_quantity - released_quantity
--   reported_quantity  = 有效 SUBMITTED WorkReport qualified+defective 汇总
--   remaining_quantity = effective_quantity - reported_quantity
--
-- 状态:
--   ACTIVE   / COMPLETED(remaining==0 派生) / CANCELLED(整份取消)
-- 部分报工后释放剩余: assigned=300/reported=180/release=120 -> effective=180/remaining=0
-- 通过 assigned/reported/released 三个数量表达事实，不堆状态。
--
-- 执行方式: 手动执行（项目无 Flyway）
-- ============================================================

CREATE TABLE IF NOT EXISTS production_execution_assignment (
  assignment_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '作业分配ID',
  execution_id      BIGINT       NOT NULL COMMENT '工序执行ID',
  order_id          BIGINT       NOT NULL COMMENT '生产工单ID(冗余:工单维度查询/过滤)',
  dispatch_id       BIGINT       DEFAULT NULL COMMENT '派工容器ID(1:1 execution)',
  dispatch_node_id  BIGINT       DEFAULT NULL COMMENT '分配时责任节点ID(谁授权的这份作业)',
  assignee_id       BIGINT       NOT NULL COMMENT '执行人ID',
  assignee_name     VARCHAR(64)  NOT NULL COMMENT '执行人姓名(快照)',
  assigned_quantity DECIMAL(18,4) NOT NULL COMMENT '分配作业数量(创建后不可直接修改)',
  released_quantity DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '释放剩余数量(默认0)',
  assignment_status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/COMPLETED/CANCELLED',
  assigned_by       BIGINT       DEFAULT NULL COMMENT '分配人ID',
  assigned_by_name  VARCHAR(64)  DEFAULT NULL COMMENT '分配人姓名(快照)',
  assigned_at       DATETIME     DEFAULT NULL COMMENT '分配时间(业务时间)',
  cancelled_by      BIGINT       DEFAULT NULL COMMENT '取消/释放操作人ID',
  cancelled_at      DATETIME     DEFAULT NULL COMMENT '取消/释放时间',
  cancel_reason     VARCHAR(500) DEFAULT NULL COMMENT '取消/释放原因',
  create_by         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (assignment_id),
  KEY idx_execution (execution_id),
  KEY idx_order (order_id),
  KEY idx_assignee_status (assignee_id, assignment_status),
  KEY idx_dispatch_node (dispatch_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序作业分配(谁做多少)';

-- WorkReport 增加 assignment_id（可空：历史数据保持 NULL，不 backfill，不伪造）
ALTER TABLE production_work_report
  ADD COLUMN assignment_id BIGINT DEFAULT NULL COMMENT '关联作业分配ID(新链路必填,历史NULL)' AFTER dispatch_node_id,
  ADD KEY idx_assignment (assignment_id);
