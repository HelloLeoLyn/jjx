-- 历史档案 AI 录入入口（菜单数据，不修改业务表结构）
-- 任务：dev-20260922-XXX（待登记正式任务码后替换）
USE `jjx_erp_db`;

SET @menu_id = (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu);
INSERT INTO sys_menu (
  menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
  menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth,
  redirect, sort, create_by, create_time, update_by, update_time, remark
)
SELECT @menu_id, '历史档案录入（AI）', 90, 63, 'archive-ai',
       'views/engineering/archive-ai/index.vue', '1', '0', 'C', '0', '0',
       'engineering:archive:import', 'MagicStick', '0,90', 'EngineeringArchiveAi',
       '1', NULL, 63, 'Codex', NOW(), 'Codex', NOW(),
       '独立 AI 识别入口，复用 engineering_archive_import，不修改表结构'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'archive-ai');

SET @ai_menu_id = (SELECT menu_id FROM sys_menu WHERE path = 'archive-ai' LIMIT 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @ai_menu_id FROM sys_role
 WHERE role_key IN ('admin', 'engineering:all', 'engineering:ops', 'engineering:review');

SELECT menu_id, menu_name, path, perms FROM sys_menu WHERE path = 'archive-ai';
