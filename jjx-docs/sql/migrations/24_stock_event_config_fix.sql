-- dev-20260828-049 库存预警事件配置补充

INSERT INTO sys_event_config
    (event_code, event_name, biz_module, event_type, kanban_module, priority,
     is_enabled, target_role, title, content, exclude_trigger)
SELECT
    'stock.expiry', '库存过期预警', 'inventory', 'notification', 'emergency', 'urgent',
    0, CAST('[]' AS JSON), '物料库存即将过期', '物料库存即将过期，请及时检查并处理。', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_event_config WHERE event_code = 'stock.expiry'
);

INSERT INTO sys_event_config
    (event_code, event_name, biz_module, event_type, kanban_module, priority,
     is_enabled, target_role, title, content, exclude_trigger)
SELECT
    'stock.obsolete', '库存呆滞预警', 'inventory', 'notification', 'emergency', 'urgent',
    0, CAST('[]' AS JSON), '物料库存存在呆滞', '物料库存已形成呆滞，请及时检查并处理。', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_event_config WHERE event_code = 'stock.obsolete'
);

-- 本次只补注册，启用与 target_role 由用户在“事件配置”界面维护。
