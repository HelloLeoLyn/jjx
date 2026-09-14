-- dev-20260914-018 / 任务 1774（口径 D6）：注册「销售发货已签收」事件
-- 背景：签收动作原先不触发任何事件（死动作）。事件必须在 sys_event_config 注册且 is_enabled=1，
--       否则 LocalEventPublisher.fire 会直接跳过（未注册=通知/任务静默丢失）。
-- 收件角色（target_role）：按用户要求**不自行拍板**，此处先注册为 '[]'（不发给任何人），
--       待 Leo 在「系统管理→事件配置」页填销售负责人角色后生效（配置驱动，无需改代码）。
-- 账期/对账起点属业务规则，同样待 Leo 定，本迁移不埋任何默认值。
INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled, target_role, title, content, exclude_trigger, close_source_events, create_time, update_time)
SELECT 'sales.delivery.received', '销售发货已签收', 'sales', 'notification', 'office', 'normal', 1, '[]',
       '发货单【{bizId}】客户已签收', NULL, 0, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.delivery.received');
