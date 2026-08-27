-- =====================================================
-- 09_review_flow.sql
-- 通用审核流水表（方案A，2026-08-27 决策）
--
-- 背景：全系统审核意见/附件存在单行覆盖丢历史
--   - sales_order.reject_reason / purchase_order.approval_comment /
--     engineering_bom.approve_remark 每次审核覆盖上一次
--   - 已有成功范式：sales_quotation_flow、sales_sample_round（每轮一条）
-- 决策（task 1134，2026-08-27）：
--   1. 范围：全系统（订单/采购/BOM/工艺；报价/样品维持现有 flow/round 表）
--   2. 目的：留痕后续可见（审计可查，前端时间线后续接）
--   3. 附件：每轮独立快照（flow.attachment_ids 引用 sys_attachment.id）
--   4. 驳回重提 = 新一轮（round_no+1），历史全留；主表单意见字段保留
--      "最新一轮"冗余供列表展示
--   5. 方案：A 通用流水表 review_flow
-- 备注：sales_order_review（重型审核表，0 行空壳）冻结不复活、不迁移；
--       报工（production_work_report）一笔一审批、行上快照已够，暂不接入。
-- =====================================================

CREATE TABLE IF NOT EXISTS `review_flow` (
  `flow_id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `biz_type`        VARCHAR(50)   NOT NULL COMMENT '业务类型：sales_order/sales_quotation/purchase_order/engineering_bom/engineering_film/production_work_report 等',
  `biz_id`          BIGINT        NOT NULL COMMENT '业务单据ID',
  `round_no`        INT           NOT NULL DEFAULT 1 COMMENT '审核轮次：提交(SUBMIT)时推进；驳回重提 = max(round_no)+1',
  `action_code`     VARCHAR(50)   NOT NULL COMMENT '动作：SUBMIT/APPROVE/REJECT/SEND/CONFIRM/CANCEL/RETURN 等',
  `action_name`     VARCHAR(50)   NULL COMMENT '动作名称（展示）',
  `from_status`     VARCHAR(30)   NULL COMMENT '动作前状态（数值/枚举原样存）',
  `to_status`       VARCHAR(30)   NULL COMMENT '动作后状态（数值/枚举原样存）',
  `operator_id`     BIGINT        NULL COMMENT '操作人ID',
  `operator_name`   VARCHAR(100)  NULL COMMENT '操作人姓名快照',
  `comment`         VARCHAR(1000) NULL COMMENT '意见/驳回原因/备注（驳回必填由业务层校验）',
  `attachment_ids`  VARCHAR(1000) NULL COMMENT '本轮审核附件 sys_attachment.id 列表（逗号分隔；每轮独立快照）',
  `extra_json`      VARCHAR(2000) NULL COMMENT '扩展：本轮关键数据快照（如报工数量、审批金额），JSON',
  `create_by`       VARCHAR(64)   NULL,
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`flow_id`),
  KEY `idx_biz_round` (`biz_type`, `biz_id`, `round_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用审核流水（每轮一条：意见+附件快照+状态迁移，防覆盖丢历史）';

-- 写入约定（不暴露独立写接口，由业务动作内部调用）：
--   ReviewFlowService.record(bizType, bizId, action, fromStatus, toStatus,
--                            comment, attachmentIds)
--   round_no 规则：该 biz 无记录=1；有 SUBMIT 记录=max(round_no)+1；
--   APPROVE/REJECT 等沿用最近一次 SUBMIT 的 round_no。
-- 接入点：
--   1. 销售订单 OrderStatusController 审核/驳回动作（biz_type=sales_order）
--   2. 采购订单审批接口（biz_type=purchase_order）
--   3. BOM/工艺路线 engineering 审核接口（biz_type=engineering_bom/engineering_film）
--   4. 报价/样品维持 sales_quotation_flow / sales_sample_round，不双写
-- 查询接口：
--   GET /system/review-flow/list?bizType=&bizId= → round_no 升序全量
--   前端时间线后续接入 TraceTimeline（按 bizType 分派）。
