-- ============================================================================
-- 145_role_menu_fix_production_outbound.sql
-- 任务码：dev-20260921-006（生产订单→确认发料 不可达 / 权限口径修复）
--
-- 问题：生产订单生成领料单后跳 /inventory/outbound 打不开（空白）。
--   角色 28「PRODUCTION 全权限」、32「PRODUCTION 操作工」被授了子菜单 33「出库作业」，
--   却没授其父菜单 18「库存管理」。后端建菜单树从 parent_id=0 的根往下挂
--   （TreeUtils.build），父节点不在集合里的节点被**静默丢弃** →
--   /system/menu/getRouters 里没有这条 → 前端 addRoute 不到 → 路由不存在 → 跳转落空页。
--
-- 修复：给这两个角色补父菜单 18「库存管理」（仅目录级，其他子菜单不额外授予，
--       因此生产角色仍只能看到「出库作业」这一项，不会看到库存台账/入库/盘点等）。
--
-- ⚠ 生效条件：受影响用户需**重新登录**（前端路由在登录时按菜单生成）。
-- ⚠ 同类隐患（本次未处理，待用户定）：角色 6「SYSTEM 全权限」有 6 个孤儿按钮菜单
--   304/305/306/307/308/309（父级 250 系统参数 / 251 文件管理 / 56 操作日志 未授予）。
--
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS，可重复执行。
-- ============================================================================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT t.role_id, t.menu_id
FROM (SELECT 28 AS role_id, 18 AS menu_id UNION ALL SELECT 32, 18) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = t.role_id AND rm.menu_id = t.menu_id
);
