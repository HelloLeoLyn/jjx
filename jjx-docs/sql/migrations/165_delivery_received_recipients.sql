-- ============================================================================
-- 165_delivery_received_recipients.sql
-- 任务码：dev-20260921-038（发货/签收链路缺口补齐）
--
-- 问题：`sales.delivery.received`（销售发货已签收）已启用、已有模板，但 `target_role = []`（空数组）
--   → LocalEventPublisher 展开角色→查用户时得到 0 个收件人，签收通知**静默无人可收**；
--   同时因为通知未落库，签收在「消息通知」里毫无痕迹（对账/账期也拿不到提醒）。
--   （2026-09-21 实测：sys_event_config.target_role='[]'，与 decisions/sales-delivery-receipt-policy-20260914.md D6 要求不符）
--
-- 修复：收件人补 销售业务操作(20) + 销售审核员(21)，与同族事件一致
--   （sales.return.approved / sales.return.received / sales.receipt.updated 均用 [20,21]）。
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

UPDATE sys_event_config
   SET target_role = '[20,21]'
 WHERE event_code = 'sales.delivery.received'
   AND (target_role IS NULL OR target_role IN ('', '[]'));
