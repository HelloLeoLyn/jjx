-- DEV-20260827-017A：报工提交时点审批人快照
ALTER TABLE `production_work_report`
  ADD COLUMN `pending_reviewer_id` BIGINT NULL COMMENT '提交时点应审批人ID（空=生产管理兜底）' AFTER `report_status`,
  ADD COLUMN `pending_reviewer_name` VARCHAR(64) NULL COMMENT '提交时点应审批人姓名快照' AFTER `pending_reviewer_id`;
