-- ============================================================================
-- 166_delivery_received_recipients_redo.sql
-- 任务码：dev-20260921-038（发货/签收链路缺口补齐）
--
-- 说明：165 的同名修复**未生效** —— `target_role` 是 JSON 列，`target_role IN ('','[]')`
--   在 JSON 与字符串比较语义下不成立（实测 `target_role='[]'` 返回 0、`JSON_LENGTH(...)=0` 返回真），
--   所以那条 UPDATE 没匹配到行。本迁移用 JSON_LENGTH 判定重做一次。
--
-- 目的：`sales.delivery.received`（销售发货已签收）收件人为空数组 → 签收通知静默无人可收；
--   补 销售业务操作(20) + 销售审核员(21)，与同族事件（sales.return.* / sales.receipt.updated）一致。
--
-- 幂等：只填空收件人，可重复执行。
-- ============================================================================

UPDATE sys_event_config
   SET target_role = '[20,21]'
 WHERE event_code = 'sales.delivery.received'
   AND (target_role IS NULL OR JSON_LENGTH(target_role) = 0);
