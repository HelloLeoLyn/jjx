-- 41_biz_requirement_event_config.sql
-- 业务需求单会签通知事件（任务 1248 P1-②）
-- submitted：通知四部门会签角色；approved/rejected：直接通知申请人（receiverId 动态）

INSERT INTO sys_event_config
(event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled, target_role, title, content, exclude_trigger)
VALUES
('biz.requirement.submitted', '业务需求提交评审-待四部门会签', 'biz', 'notification', 'office', 'high', 1,
 '[17,28,26,23,29]',
 '业务需求【{requirementNo}】待四部门会签',
 '需求「{title}」已提交评审，请到 业务管理-需求管理 完成本部门会签（工程/制造/采购仓库/品管全部同意后生效）。', 1),
('biz.requirement.approved', '业务需求会签通过', 'biz', 'notification', 'office', 'normal', 1,
 NULL,
 '业务需求【{requirementNo}】会签通过，已生效',
 '您提交的需求「{title}」已通过四部门会签，可开始执行。', 0),
('biz.requirement.rejected', '业务需求会签未通过', 'biz', 'notification', 'office', 'normal', 1,
 NULL,
 '业务需求【{requirementNo}】会签未通过',
 '您提交的需求「{title}」会签未通过：{remark}。请修改后重新提交。', 0)
ON DUPLICATE KEY UPDATE event_name = VALUES(event_name);
