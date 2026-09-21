-- ============================================================================
-- 148_role_menu_fix_system_orphans.sql
-- 任务码：dev-20260921-006（承接 145：菜单"只授子不授父"导致节点被静默丢弃）
--
-- 问题：角色 6「SYSTEM 全权限」被授了 6 个按钮菜单，但它们的**父级页面/目录没授**
--   → 后端菜单树（TreeUtils.build，从 parent_id=0 往下挂）把这些孤儿节点静默丢弃
--   → 权限点看起来有、但用户根本进不到那些页面（按钮永不可达）。
--
--   孤儿 → 缺的祖先：
--     304 编辑系统参数(父 250 系统参数)
--     305 手动备份 / 306 预警检查 / 307 产品文件迁移(父 251 文件管理，其父 299 运维监控)
--     308 删除操作日志 / 309 清空操作日志(父 56 操作日志，其父 299 运维监控)
--   （298 基础配置、1 系统管理 角色 6 已有）
--
-- 修复：补 250 系统参数 / 251 文件管理 / 56 操作日志 / 299 运维监控。
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS，可重复执行。
-- ============================================================================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, t.menu_id
FROM (SELECT 250 AS menu_id UNION ALL SELECT 251 UNION ALL SELECT 56 UNION ALL SELECT 299) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 6 AND rm.menu_id = t.menu_id
);
