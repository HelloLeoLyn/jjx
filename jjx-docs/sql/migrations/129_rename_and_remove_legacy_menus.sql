-- ============================================================================
-- 129: 命名收敛 + 旧入口移除——用户 2026-09-17 定
--   ① 新页面改名：成品检验（检验批） → 成品检验（去掉后缀）
--   ② 旧成品入口移除：生产质检(264) / 质检报告(265) 及其按钮子菜单、角色授权一并删除
--      （后端"完工自动建 FQC 单"已停用，删除菜单不会留下能点的旧操作面）
--   ③ 已回退的来料检验批入口移除：来料检验（检验批）(376) 及其按钮(380/383)
--   保留：来料检验(330，原方案)、不合格品处置(333，来料隔离处置)、成品检验(377)、不良台账(378)、抽样方案(379)
-- 幂等：按 menu_id 删除，重复执行 0 行。
-- ============================================================================
USE `jjx_erp_db`;

-- ① 改名
UPDATE sys_menu SET menu_name = '成品检验', update_time = NOW() WHERE menu_id = 377;

-- ② + ③ 删除授权（先删 role_menu，再删菜单；含子按钮）
DELETE rm FROM sys_role_menu rm
WHERE rm.menu_id IN (264, 265, 376)
   OR rm.menu_id IN (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id IN (264, 265, 376)) x);

DELETE FROM sys_menu
WHERE menu_id IN (264, 265, 376)
   OR parent_id IN (264, 265, 376);

-- ④ 核验：质量管理(338) 下的菜单应只剩 来料检验 / 不合格品处置 / 成品检验 / 不良台账 / 抽样方案
SELECT '核验：质量管理(338) 下菜单' AS check_point;
SELECT menu_id, menu_name, menu_type, visible, perms, path
FROM sys_menu WHERE parent_id = 338 ORDER BY order_num, menu_id;
SELECT '核验：已删除的旧菜单应为 0 行' AS check_point;
SELECT COUNT(*) AS legacy_left FROM sys_menu WHERE menu_id IN (264, 265, 376) OR parent_id IN (264, 265, 376);
SELECT '核验：角色授权里不应再引用已删菜单' AS check_point;
SELECT COUNT(*) AS dangling_role_menu FROM sys_role_menu rm
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) m WHERE m.menu_id = rm.menu_id);
