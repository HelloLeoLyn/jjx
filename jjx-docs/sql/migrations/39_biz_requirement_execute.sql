-- 39_biz_requirement_execute.sql
-- 业务需求单：执行→关闭流转字段 + 会签轮次（任务 1248 P1）
-- 状态机：3已通过 →(开始执行)→ 4执行中 →(关闭,登记结果)→ 5已关闭
ALTER TABLE biz_requirement
  ADD COLUMN execute_by varchar(50) NULL COMMENT '执行人' AFTER review_remark,
  ADD COLUMN execute_time datetime NULL COMMENT '开始执行时间' AFTER execute_by,
  ADD COLUMN execute_result varchar(500) NULL COMMENT '执行结果（关闭时登记）' AFTER execute_time,
  ADD COLUMN current_round int NOT NULL DEFAULT 0 COMMENT '当前会签轮次（submit 时 +1）' AFTER execute_result;
