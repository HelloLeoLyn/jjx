-- ============================================================================
-- 134_notify_production_on_inbound.sql
-- 任务码：dev-20260918-005
--
-- 目的：入库确认完成（inventory.inbound.confirmed）后，把「库存已可用」知会生产侧，
--       让生产据此安排领料 / 开工 —— 这是 133 号迁移之外补的第 4 条（用户 2026-09-18 确认要做）。
--
-- 背景：该事件原 target_role=[23]（INVENTORY 业务操作，当前 0 个用户），
--       生产侧任何角色都收不到"料已入库"的通知；生产目前只在
--       订单客户确认 / 订单提交生产 / 工单开始 / 报工 这几个环节被通知。
--
-- 角色现状（2026-09-18 实测，全库 26 个用户）：
--       28 PRODUCTION 全权限 1 人（本次立刻能收到）
--       29 PRODUCTION 业务操作 0 人（语义岗，授权后自动开始收）
--       30 PRODUCTION 派工主管 2 人、32 PRODUCTION 操作工 8 人（本次未加，避免通知面过宽；
--          若需要派工主管也收，再追加 30 即可）
--
-- 口径：event_type='both' 的待办归属角色取 target_role 第一个元素（仍为 23），
--       所以本次只追加「通知」收件人，不会给生产新增待办任务；标题/正文保持不变。
-- 幂等：JSON_CONTAINS 判重，可重复执行。
-- ============================================================================

UPDATE sys_event_config
   SET target_role = JSON_ARRAY_APPEND(target_role, '$', 28)
 WHERE event_code = 'inventory.inbound.confirmed'
   AND NOT JSON_CONTAINS(COALESCE(target_role, JSON_ARRAY()), '28');

UPDATE sys_event_config
   SET target_role = JSON_ARRAY_APPEND(target_role, '$', 29)
 WHERE event_code = 'inventory.inbound.confirmed'
   AND NOT JSON_CONTAINS(COALESCE(target_role, JSON_ARRAY()), '29');

-- ============================================================================
-- 执行后自检（应为 [23, 28, 29]，close_source_events 仍为 quality.iqc.approved）：
--   SELECT id, event_code, event_type, target_role, title, content, close_source_events
--     FROM sys_event_config WHERE event_code = 'inventory.inbound.confirmed';
-- ============================================================================
