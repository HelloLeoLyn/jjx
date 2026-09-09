-- 71_add_common_notify_default_roles.sql
-- dev-20260909-003：/common/notify-task 未传 roleKeys 时的默认通知角色（可后台修改，逗号分隔 role_key）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
VALUES ('notify_task_default_roles', 'production:all,admin', '通用催办默认通知角色', 'business',
        'dev-20260909-003 /common/notify-task 未传 roleKeys 时的默认接收角色，逗号分隔 role_key（如 production:all,admin）', 90, 1)
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);
