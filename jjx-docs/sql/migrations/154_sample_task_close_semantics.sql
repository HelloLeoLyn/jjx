-- ============================================================================
-- 154_sample_task_close_semantics.sql
-- 任务码：dev-20260921-017（样品链任务状态止血）
--
-- 问题（2026-09-21 用户报）：样品单 SP260921001 工程接单（状态→打样中）后，
--   「样品单【SP260921001】已创建，请安排打样」待办（task 2030）直接被置为已完成(10)，
--   completed_time=15:31:28，update_by 为空 —— 是 sample.accepted 的"办结关闭"逻辑自动干的。
-- 语义应为：接单/开始打样 = 任务「进行中」；打样完成 = 任务「已完成」。
--
-- 现状配置错配：
--   sample.accepted(160) close_source_events = sample.created   ← 一接单就把待办关掉（错）
--   sample.started(161)  close_source_events = sample.created   ← 开始打样也关（错）
--   sample.ready(9)      close_source_events = NULL             ← 打样完成该关却不关
--
-- 本次止血（配置层，不动代码）：
--   sample.accepted / sample.started → close_source_events = NULL（不再提前办结）
--   sample.ready                     → close_source_events = sample.created（打样完成才办结）
--
-- ⚠️ 局限：配置层只有"关闭"一种动作，做不到把任务推进为「进行中」——打样期间该待办会停在
--    「待开始」。一一对应（待打样=待开始 / 打样中=进行中 / 打样完成=已完成）由
--    dev-20260921-016「事件→任务状态联动通用方案」的 task_effects 实现，本迁移只是止血。
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

-- 1) 接单 / 开始打样：不再提前办结「请安排打样」待办
UPDATE sys_event_config
   SET close_source_events = NULL
 WHERE event_code IN ('sample.accepted', 'sample.started');

-- 2) 打样完成：此时才办结「请安排打样」待办
UPDATE sys_event_config
   SET close_source_events = 'sample.created'
 WHERE event_code = 'sample.ready';
