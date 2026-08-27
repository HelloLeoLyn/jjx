-- 07_sample_submitted_notification_title.sql
-- 修复 sample.submitted 通知标题缺失（Field 'title' doesn't have a default value）
--
-- 背景：
--   sys_event_config 中 sample.submitted 的 title 为 NULL
--   → LocalEventPublisher.resolveTemplate(NULL, payload) 返回 NULL
--   → INSERT sys_notification 时 title NOT NULL 约束失败，通知写入失败。
--
-- 变量依据：
--   payload 中没有 sampleOrderNo 字段；bizId 由 @Event(bizId="#orderId") SpEL 注入
--   （即样品单主键 orderId），resolveTemplate 支持 ${xxx}/{xxx} 直接取 payload key。
--   标题写法与同族事件 sample.created / sample.restarted / sample.cancelled 等一致。
--
-- 幂等：仅当 title 为 NULL 或空串时更新，不覆盖人工配置。
UPDATE sys_event_config
SET title = '样品单【{bizId}】已提交审核'
WHERE event_code = 'sample.submitted'
  AND (title IS NULL OR TRIM(title) = '');
