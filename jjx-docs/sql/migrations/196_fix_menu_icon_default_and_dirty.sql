-- ============================================================================
-- 196_fix_menu_icon_default_and_dirty.sql
-- 任务：dev-20260922-006（用户 2026-09-22 12:03「一起做」）
--
-- 起因：前端报 InvalidCharacterError: Failed to execute 'createElement' ... The tag name
--       provided ('#') is not a valid name。
-- 根因：`sys_menu.icon` 列默认值是 '#'；迁移 179 插入 menu 390「出货检验」时没写 icon
--       → 落库为 '#'；前端 layout/components/SidebarItem.vue 用 `<component :is="item.icon">`
--       渲染 → Vue 拿 '#' 当标签名 createElement → 抛错。
--       全库 39 个 icon='#' 中只有 390 是**可见菜单(C 型、visible=0、status=0)**，其余全是 F 按钮
--       （不渲染图标）。
--
-- 处理（三件）：
--   ① 清理脏 icon：`icon='#'` 或纯空白的菜单 → NULL（含 F 按钮，统一口径）
--   ② 【断根】`sys_menu.icon` 列默认值 '#' → NULL：以后漏填就是 NULL，前端跳过渲染而不是炸页面
--   ③ 给 390「出货检验」补一个合法图标（与同级 377「成品检验」一致的 Checked）
--
-- 幂等：UPDATE 均带条件；ALTER ... SET DEFAULT 可重复执行。
-- 前置 guard 备份：~/jjx-backups/sys_menu_icon_fix_before_20260922-1204.sql
--   （md5 980a749f1f1c49a4ba34e66380c3e21b）
-- ============================================================================
USE `jjx_erp_db`;

-- ① 脏 icon（'#' / 空白）→ NULL
UPDATE sys_menu SET icon = NULL, update_time = NOW()
 WHERE icon = '#' OR TRIM(IFNULL(icon, '')) = '';

-- ② 断根：列默认值 # → NULL
ALTER TABLE sys_menu ALTER COLUMN icon SET DEFAULT NULL;

-- ③ 390「出货检验」补合法图标
UPDATE sys_menu SET icon = 'Checked', update_time = NOW()
 WHERE menu_id = 390 AND (icon IS NULL OR icon = '');

-- ── 核验 ──────────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM sys_menu WHERE icon = '#') AS icon_hash_should_be_0,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_type IN ('M','C') AND IFNULL(icon,'') = '') AS visible_menu_no_icon,
       (SELECT column_default FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND column_name = 'icon') AS icon_default,
       (SELECT icon FROM sys_menu WHERE menu_id = 390) AS menu390_icon;
