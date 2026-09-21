-- ============================================================================
-- 135_notify_production_lead_on_inbound.sql
-- 任务码：dev-20260918-006
--
-- 目的：入库确认完成（inventory.inbound.confirmed）的通知收件人再补一个岗位——
--       30 PRODUCTION 派工主管（2026-09-18 实测 2 个用户，是实际派工/安排开工的人）。
--       承接 134 号迁移（当时只加了 28 全权限 与 29 业务操作），用户 2026-09-18 确认要加 30。
--
-- 背景：该事件 target_role 现状为 [23, 28, 29]，本次追加后为 [23, 28, 29, 30]。
--       待办归属角色取第一个元素（仍为 23，未改变），标题/正文保持不变。
--       仍**不加** 32 PRODUCTION 操作工（8 人），避免通知面过宽。
--
-- 幂等：JSON_CONTAINS 判重，可重复执行。
-- ============================================================================

UPDATE sys_event_config
   SET target_role = JSON_ARRAY_APPEND(target_role, '$', 30)
 WHERE event_code = 'inventory.inbound.confirmed'
   AND NOT JSON_CONTAINS(COALESCE(target_role, JSON_ARRAY()), '30');

-- ============================================================================
-- 执行后自检（应为 [23, 28, 29, 30]，close_source_events 仍为 quality.iqc.approved）：
--   SELECT id, event_code, event_type, target_role, title, content, close_source_events
--     FROM sys_event_config WHERE event_code = 'inventory.inbound.confirmed';
-- ============================================================================
