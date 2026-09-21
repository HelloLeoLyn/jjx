-- ============================================================================
-- 146_oper_log_param_mediumtext.sql
-- 任务码：dev-20260921-003
--
-- 目的：sys_oper_log.oper_param 由 varchar(2000) 扩为 MEDIUMTEXT，
--       避免「请求参数稍长 → 整条操作日志写不进库」的追溯断点。
--
-- 背景（实测，2026-09-21）：
--   11:15:39 提交来料检验（PO202609210001，2 条明细 × 8 个检测项）时日志落库失败：
--     logs/jjx-server.log:724-731
--     ERROR LogSaveService - 保存操作日志失败:
--       Data truncation: Data too long for column 'oper_param' at row 1
--   后果：该次「提交入库审批」在 sys_oper_log 里没有任何记录（同一时段只有
--   PO202609210002 那一条，因为它只有 1 条明细、JSON 1605 字符，恰好压线入库：
--   sys_oper_log id=68）。排查「是谁、什么时候提交的」时证据缺失。
--   代码侧：LogSaveService.saveOperLog(:36) 直接 insert，不做截断，失败只打 ERROR 不重试。
--
-- 为什么直接扩列而不是在代码里截断：截断会把证据本身砍掉；oper_param 上没有索引
--   （sys_oper_log 只有 idx_user_time / idx_module_time / idx_biz / idx_trace），
--   该表当前 68 行，改列宽是轻量 DDL；MEDIUMTEXT 列不进行内，不影响既有查询与 LIKE 过滤。
--
-- 幂等：MODIFY COLUMN 直接写目标类型，可重复执行。
-- ============================================================================

ALTER TABLE sys_oper_log
  MODIFY COLUMN oper_param MEDIUMTEXT NULL COMMENT '请求参数（原 varchar(2000) 超长会丢日志，2026-09-21 dev-20260921-003 扩容）';

-- ============================================================================
-- 执行后自检：
--   SHOW COLUMNS FROM sys_oper_log LIKE 'oper_param';   -- 应为 mediumtext
-- ============================================================================
