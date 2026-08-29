-- dev-20260828-046: 生产逻辑身份改为 role_key 名单配置（幂等）
INSERT INTO sys_config
    (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time, update_time)
SELECT
    'production_admin', 'production:all', '生产管理者角色名单', 'production_config',
    '多个 role_key 用英文逗号分隔，填写 sys_role.role_key，如 production:all,production:dispatch_mgr',
    COALESCE((SELECT MAX(c.sort_order) + 1 FROM sys_config c WHERE c.config_group = 'production_config'), 1),
    1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_group = 'production_config' AND config_key = 'production_admin'
);

UPDATE sys_config
SET config_value = 'production:all',
    config_name = '生产管理者角色名单',
    remark = '多个 role_key 用英文逗号分隔，填写 sys_role.role_key，如 production:all,production:dispatch_mgr'
WHERE config_group = 'production_config'
  AND config_key = 'production_admin';

INSERT INTO sys_config
    (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time, update_time)
SELECT
    'production_global_scope', 'production:all', '全局生产数据范围角色名单', 'production_config',
    '多个 role_key 用英文逗号分隔，填写 sys_role.role_key，如 production:all,production:dispatch_mgr',
    COALESCE((SELECT MAX(c.sort_order) + 1 FROM sys_config c WHERE c.config_group = 'production_config'), 1),
    1, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_group = 'production_config' AND config_key = 'production_global_scope'
);

UPDATE sys_config
SET config_name = '全局生产数据范围角色名单',
    remark = '多个 role_key 用英文逗号分隔，填写 sys_role.role_key，如 production:all,production:dispatch_mgr'
WHERE config_group = 'production_config'
  AND config_key = 'production_global_scope';
