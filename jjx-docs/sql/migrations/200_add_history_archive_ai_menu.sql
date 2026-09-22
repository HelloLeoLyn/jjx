-- 200_add_history_archive_ai_menu.sql
-- 历史档案 AI 录入入口（菜单数据，不修改业务表结构）
-- 备份人: Codex / 改号与口径统一: dahuang
-- 任务：dev-20260922-025（AI 录入入口与接口）；改号与 perms 统一属收尾任务 dev-20260922-024
-- 风险：低（只增菜单 + 授权，不动业务表结构）；前置 guard：~/jjx-backups/sys_menu_archive_ai_perms_20260922-1918.sql
-- 涉及表：sys_menu / sys_role_menu
-- 2026-09-22 收尾改动（用户 19:18 授权「你来决定/你来统一」）：
--   ① 改号 199 → 200：与 199_rename_quality_menus.sql 撞号（后建者改号；db-migrate --status 报「迁移号重复」）
--   ② perms 口径统一：库中 menu_id=392 实际为 'engineering:archive:ai'，本文件为 'engineering:archive:import'
--      → 统一取 'engineering:archive:import'（后端 AI 接口 @SaCheckPermission 用的就是这个；
--        363「上传识别」按钮也已声明该权限。'archive:ai' 未在任何按钮/注解处声明，属幽灵权限）
--      已同步 UPDATE sys_menu SET perms='engineering:archive:import' WHERE menu_id=392；
--      392 已授权的 4 个角色（admin / engineering:all / engineering:ops / engineering:review）均同时持有 363，改后可见性不变。
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
