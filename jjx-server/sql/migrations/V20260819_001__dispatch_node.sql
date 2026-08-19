-- =====================================================
-- V20260819_001：派工责任链节点表 production_dispatch_node
-- 定位：一次生产任务责任持有实例（某个责任主体在某一时间段内正式持有该任务责任的一次历史实例）
-- 架构：ProductionOperationExecution 1:1 ProductionDispatch 1:N ProductionDispatchNode
-- 唯一 ACTIVE：生成列 active_guard（ACTIVE→1，其他→NULL）+ UNIQUE(dispatch_id, active_guard)
--           已通过真实 MySQL 8.4.10 实测（插入第二个 ACTIVE 报 1062；UPDATE 释放后可再插 ACTIVE）
-- 外键策略：遵循项目现状，不使用物理 FOREIGN KEY，dispatch_id/parent_node_id 逻辑关联
-- 2026-08-19：P1-A 基础设施（P1-B 起 Node 才接管业务读；本表初始 0 条业务数据）
-- =====================================================

CREATE TABLE `production_dispatch_node` (
  `node_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `dispatch_id`      BIGINT       NOT NULL COMMENT '派工单ID(production_dispatch.dispatch_id)',
  `parent_node_id`   BIGINT       NULL COMMENT '上级节点ID(第1级=NULL，表示源头主管直派；责任来源节点)',
  `assignee_type`    VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '责任主体类型：USER(P1第一版仅支持)',
  `assignee_id`      BIGINT       NOT NULL COMMENT '责任主体ID(用户ID)',
  `assignee_name`    VARCHAR(64)  NOT NULL COMMENT '责任主体姓名快照(改昵称不影响历史)',
  `org_id`           BIGINT       NULL COMMENT '责任主体当时所属组织ID快照',
  `org_name`         VARCHAR(100) NULL COMMENT '责任主体当时所属组织名称快照',
  `org_path`         VARCHAR(500) NULL COMMENT '责任主体当时所属组织祖先路径快照(如"1/5/6/7")',
  `node_status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '节点状态：ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED',
  `assigned_by`      BIGINT       NULL COMMENT '本次责任由谁指派(用户ID)',
  `assigned_by_name` VARCHAR(64)  NULL COMMENT '指派人姓名快照',
  `assigned_at`      DATETIME     NULL COMMENT '本次责任正式生效时间',
  `closed_at`        DATETIME     NULL COMMENT '本次责任周期结束时间(流转走/完成/取消)',
  `remark`           VARCHAR(500) NULL COMMENT '备注/退回原因/迁移说明(LEGACY_BACKFILL)',
  `create_by`        VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        VARCHAR(64)  NULL COMMENT '更新人',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `active_guard`     TINYINT GENERATED ALWAYS AS (CASE WHEN `node_status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT '唯一ACTIVE守卫列(ACTIVE→1，其他→NULL；DB生成，Java不写)',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_dispatch_active` (`dispatch_id`, `active_guard`),
  KEY `idx_dispatch` (`dispatch_id`),
  KEY `idx_assignee_status` (`assignee_id`, `node_status`),
  KEY `idx_parent` (`parent_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工责任链节点(责任持有实例)';
