-- ============================================================================
-- 125: 修复新菜单图标（icon 默认 '#' 导致侧边栏渲染崩溃）——dev-20260917-009/010/012
-- 现象：前端报 `InvalidCharacterError: Failed to execute 'createElement' ... ('#')`（permission.ts 路由错误）。
-- 根因：sys_menu.icon 列默认值为 '#'；迁移 122 插入新菜单时未给 icon，落库为 '#'
--       → 侧边栏对 C 类菜单渲染 <component :is="item.icon"> → 把 '#' 当成 HTML 标签名 → createElement 抛错。
--       （F 类按钮不进侧边栏，历史 41 行 icon='#' 无此现象；本次只修新增页面菜单与按钮。）
-- 幂等：按 menu_id 直接覆盖为固定值。
-- ============================================================================
USE `jjx_erp_db`;

UPDATE sys_menu SET icon = 'Box'     WHERE menu_id = 376; -- 来料检验（检验批）
UPDATE sys_menu SET icon = 'Checked' WHERE menu_id = 377; -- 成品检验（检验批）
UPDATE sys_menu SET icon = 'Warning' WHERE menu_id = 378; -- 不良台账
UPDATE sys_menu SET icon = 'Setting' WHERE menu_id = 379; -- 抽样方案
UPDATE sys_menu SET icon = 'Setting' WHERE menu_id IN (380, 381, 383, 384, 386); -- 按钮（F，不渲染）

SELECT '核验：C 类菜单不应再有 icon=#（应为 0 行）' AS check_point;
SELECT COUNT(*) AS c_menu_hash_icon FROM sys_menu WHERE menu_type = 'C' AND icon = '#';
SELECT menu_id, menu_name, menu_type, icon FROM sys_menu WHERE menu_id BETWEEN 376 AND 386 ORDER BY menu_id;
