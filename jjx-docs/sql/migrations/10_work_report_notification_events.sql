-- dev-20260827-013 报工通知联动
-- sys_event_config 结构与 quotation/sample 事件保持一致。
-- receiverId 由 @Event payload 动态指定；target_role=[29] 仅作为第一层任务无父执行人时的生产角色兜底。

INSERT INTO sys_event_config
    (event_code, event_name, biz_module, event_type, kanban_module, priority,
     is_enabled, target_role, title, content, exclude_trigger)
VALUES
    ('production.work-report.submitted', '报工提交', 'production', 'notification',
     'production', 'high', 1, CAST('[29]' AS JSON),
     '新报工待审批：{orderNo}',
     '报工单#{reportId}，任务#{taskId}，合格{qualifiedQuantity}，不良{defectiveQuantity}，请及时审批。', 1),
    ('production.work-report.approved', '报工审批通过', 'production', 'notification',
     'production', 'normal', 1, NULL,
     '报工已审批通过：{orderNo}',
     '报工单#{reportId}已通过，合格{qualifiedQuantity}，不良{defectiveQuantity}。', 0),
    ('production.work-report.rejected', '报工审批驳回', 'production', 'notification',
     'production', 'high', 1, NULL,
     '报工被驳回：{orderNo}',
     '报工单#{reportId}被驳回，合格{qualifiedQuantity}，不良{defectiveQuantity}，请查看原因后处理。', 0)
ON DUPLICATE KEY UPDATE
    event_name = VALUES(event_name),
    biz_module = VALUES(biz_module),
    event_type = VALUES(event_type),
    kanban_module = VALUES(kanban_module),
    priority = VALUES(priority),
    is_enabled = VALUES(is_enabled),
    target_role = VALUES(target_role),
    title = VALUES(title),
    content = VALUES(content),
    exclude_trigger = VALUES(exclude_trigger);
