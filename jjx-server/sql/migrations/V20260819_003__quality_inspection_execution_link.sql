-- ============================================================
-- V20260819_003__quality_inspection_execution_link.sql
-- P3-B: QualityInspection 从"只挂订单"升级为可关联工序/报工
-- ------------------------------------------------------------
-- 变更:
--   1. 新增 execution_id   (可空) 关联 production_operation_execution
--   2. 新增 work_report_id (可空) 关联 production_work_report
--   3. total_qty / pass_qty / fail_qty INT -> DECIMAL(18,4)
--      (数量正式表示质量事实: 检验数量/认可合格/判定不合格)
--   4. 新增 idx_execution_id / idx_work_report_id 索引
--   5. 不加物理 FK (与 P1/P2 migration 一致)
--
-- 关联约定 (领域规则, P3-A 拍板):
--   FQC : order_id 有值, execution_id 有值, work_report_id = NULL
--   IPQC: order_id 有值, execution_id 有值, work_report_id 可空
--   IQC/OQC: 不强制关联 execution/workReport, 保持兼容
--   dispatch_node_id 不冗余 (责任追溯走 WorkReport -> DispatchNode)
--
-- 执行方式: 手动执行 (项目无 Flyway)
-- 前置检查: production_quality_inspection 当前 0 行, 无历史数据兼容负担
-- ============================================================

ALTER TABLE production_quality_inspection
  ADD COLUMN execution_id   BIGINT       DEFAULT NULL COMMENT '关联工序执行ID(IPQC/FQC; IQC/OQC可空)' AFTER order_id,
  ADD COLUMN work_report_id BIGINT       DEFAULT NULL COMMENT '关联报工ID(IPQC可空/推荐; FQC=NULL; IQC/OQC=NULL)' AFTER execution_id,
  MODIFY COLUMN total_qty   DECIMAL(18,4) DEFAULT 0.0000 COMMENT '检验总数(实际检验数量)',
  MODIFY COLUMN pass_qty    DECIMAL(18,4) DEFAULT 0.0000 COMMENT '合格数(质量认可合格数量)',
  MODIFY COLUMN fail_qty    DECIMAL(18,4) DEFAULT 0.0000 COMMENT '不合格数(质量判定不合格数量)',
  ADD KEY idx_execution_id   (execution_id),
  ADD KEY idx_work_report_id (work_report_id);
