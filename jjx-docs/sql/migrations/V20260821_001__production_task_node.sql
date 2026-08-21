-- =====================================================
-- V20260821_001：生产任务树节点表 production_task_node
-- 定位：统一 TaskNode 模型，替代旧 DispatchNode + Assignment 双模型（旧模型代码已删除）
-- 架构：ProductionOperationExecution 1:N ProductionTaskNode（每道工序一棵任务树）
-- 核心语义：
--  - 根节点（parent_node_id = NULL）代表该工序全部任务数量（root.task_quantity = execution 计划数量）
--  - 所有节点语义一致，不区分"责任节点/作业节点"；持有人可自己执行报工，也可部分分配下级
--  - 分配任务 = 创建子节点；子节点数量总和不能超过父节点当前可分配数量
--  - 数量公式：effective = task_quantity - recalled_quantity
--    childOccupied = Σ 直接子节点 effective（已取消节点 effective=0，自然不占用）
--    availableToAssign = effective - childOccupied - selfReported
--    （selfReported = 本节点持有人的有效报工量，从 WorkReport 动态汇总；本表不落完成量）
-- 关键决策：
--  - 无物理 FOREIGN KEY（遵循项目现状，逻辑关联由 Service 校验）
--  - assignee_name 快照（姓名可变，历史展示稳定）
--  - 无 completed_quantity 列：TaskNode 完成量禁止持久化回写，统一从 WorkReport 动态汇总
--  - recalled_quantity：已收回数量（已分配子节点但收回，P2 支持）
--  - 无 status 列：状态为动态投影（CANCELLED=effective 0 且无有效报工；COMPLETED=selfRemaining 0 且子树闭环；其余 ACTIVE），
--    避免为显示状态制造第二事实源
--  - 旧表 production_dispatch / production_dispatch_node / production_execution_assignment 本轮不 DROP，
--    后续新模型稳定后一起清理
-- =====================================================

CREATE TABLE `production_task_node` (
  `task_node_id`       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务节点ID',
  `execution_id`       BIGINT        NOT NULL COMMENT '工序执行记录ID(任务树归属)',
  `parent_node_id`     BIGINT        NULL COMMENT '父节点ID(NULL=根节点，代表工序全部任务数量)',
  `assignee_id`        BIGINT        NOT NULL COMMENT '节点持有人(执行人)用户ID',
  `assignee_name`      VARCHAR(64)   NULL COMMENT '节点持有人姓名快照',
  `task_quantity`      DECIMAL(18,4) NOT NULL COMMENT '节点任务数量',
  `recalled_quantity`  DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已收回数量(已分配子节点但收回，P2 支持)',
  `remark`             VARCHAR(500)  NULL COMMENT '备注',
  `create_by`          VARCHAR(64)   NULL COMMENT '创建人',
  `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          VARCHAR(64)   NULL COMMENT '更新人',
  `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`task_node_id`),
  KEY `idx_execution` (`execution_id`),
  KEY `idx_parent` (`parent_node_id`),
  KEY `idx_assignee` (`assignee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产任务树节点(统一TaskNode模型，替代旧DispatchNode+Assignment)';
