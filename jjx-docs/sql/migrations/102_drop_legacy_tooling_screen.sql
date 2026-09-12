-- dev-20260912-011 / task 1740
-- 旧 production_tooling 与 jjx_screen_master 将由新的工程工艺资源模型取代。
-- 本迁移有意丢弃线上旧表；恢复数据请使用迁移入口生成的迁移前全库备份。

-- 先移除角色授权，再移除两套旧功能的菜单和按钮，避免留下无效路由与权限。
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms LIKE 'production:tooling:%'
   OR m.perms LIKE 'engineering:screen:%'
   OR m.component IN ('views/production/tooling/index.vue', 'views/engineering/screen/index.vue');

DELETE FROM sys_menu
WHERE perms LIKE 'production:tooling:%'
   OR perms LIKE 'engineering:screen:%'
   OR component IN ('views/production/tooling/index.vue', 'views/engineering/screen/index.vue');

-- 旧工装编号配置只服务于已下线的 production_tooling。
DELETE FROM sys_config
WHERE config_key IN ('tooling_no_rule', 'biz_no_rule.tooling');

-- 破坏性操作：旧数据只保留在本次迁移前全库备份中，不回填、不兼容。
DROP TABLE IF EXISTS production_tooling;
DROP TABLE IF EXISTS jjx_screen_master;

-- 执行记录：2026-09-12 21:43 Codex；ops.schema.version=102；
-- 迁移前备份 md5=5bd8e1047c04bd37768b3138ae911ffe。
