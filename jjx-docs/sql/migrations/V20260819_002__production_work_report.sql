-- =====================================================
-- V20260819_002：生产报工表 production_work_report
-- 定位：一次不可覆盖的生产报工事实
-- 架构：ProductionOperationExecution 1:N ProductionWorkReport；ProductionDispatchNode 1:N ProductionWorkReport
-- 领域定义：这次生产发生在哪道工序、当时由哪个 DispatchNode 承担责任、谁提交报工、
--          本次实际使用设备、合格多少、不良多少、人工/机器工时、生产时间区间
-- 关键决策：
--  - 无 report_no（用户不使用报工编号，reportId 足够）
--  - 无 input/output_quantity（本次实际产出 = qualified + defective；output 由 P2-C 作为 execution projection 计算）
--  - 无 org 快照（责任组织通过 dispatch_node_id → node.org snapshot 获取）
--  - dispatch_id 冗余（查询便利/历史稳定/Trace，虽可推导但值得）
--  - 无物理 FOREIGN KEY（遵循项目现状，逻辑关联由 Service 校验）
--  - report_status：SUBMITTED/CANCELLED（不可编辑，只可撤销；撤销审计字段一次建齐）
-- 2026-08-19：P2-B 基础设施（P2-C 起才实现 SUBMIT/CANCEL 动作；本表初始 0 条业务数据）
-- =====================================================

CREATE TABLE `production_work_report` (
  `report_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '报工ID',
  `order_id`           BIGINT       NOT NULL COMMENT '生产订单ID(冗余引用，便于追溯查询)',
  `order_no`           VARCHAR(50)  NULL COMMENT '工单编号(冗余)',
  `execution_id`       BIGINT       NOT NULL COMMENT '工序执行记录ID(生产事实主体)',
  `dispatch_id`        BIGINT       NULL COMMENT '派工单ID(冗余；需与 node.dispatchId 一致)',
  `dispatch_node_id`   BIGINT       NOT NULL COMMENT '报工时责任节点ID(责任锚点)',
  `reporter_id`        BIGINT       NOT NULL COMMENT '报工提交人ID(P2-C 默认须=ACTIVE assignee，库不强制)',
  `reporter_name`      VARCHAR(64)  NOT NULL COMMENT '报工提交人姓名快照',
  `equipment_id`       BIGINT       NULL COMMENT '本次实际使用设备ID(可空=人工工序无设备)',
  `equipment_name`     VARCHAR(200) NULL COMMENT '本次实际使用设备名称(快照)',
  `qualified_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '本次合格数量',
  `defective_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '本次不良数量',
  `labor_hours`        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本次人工工时',
  `machine_hours`      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本次机器工时',
  `work_start_time`    DATETIME     NULL COMMENT '本次生产开始时间(可空)',
  `work_end_time`      DATETIME     NULL COMMENT '本次生产结束时间(可空；P2-C 校验 end>=start)',
  `report_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报工正式提交时间(Service 显式设置)',
  `defect_reason`      VARCHAR(500) NULL COMMENT '不良原因(P2 V1 单字段，P3 再做缺陷明细)',
  `remark`             VARCHAR(500) NULL COMMENT '备注(提交后不可变)',
  `report_status`      VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED已提交/CANCELLED已撤销',
  `cancelled_by`       BIGINT       NULL COMMENT '撤销人ID',
  `cancelled_by_name`  VARCHAR(64)  NULL COMMENT '撤销人姓名',
  `cancelled_at`       DATETIME     NULL COMMENT '撤销时间',
  `cancel_reason`      VARCHAR(500) NULL COMMENT '撤销原因(P2-C 必填)',
  `create_by`          VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          VARCHAR(64)  NULL COMMENT '更新人',
  `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  KEY `idx_execution` (`execution_id`),
  KEY `idx_execution_status` (`execution_id`, `report_status`),
  KEY `idx_dispatch_node` (`dispatch_node_id`),
  KEY `idx_reporter_status` (`reporter_id`, `report_status`),
  KEY `idx_report_time` (`report_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产报工(一次不可覆盖的生产数量/工时事实)';
