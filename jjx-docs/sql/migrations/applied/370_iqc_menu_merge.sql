-- 任务码：dev-20261117-022
-- 目的：来料检验 P0 入口合并——「来料检验批」菜单并入 /inventory/iqc 的「检验批台账」页签；
--       隐藏 menu 403，旧路由由前端 constantRoutes 重定向到 /inventory/iqc?tab=lots。
-- 影响面：仅 sys_menu.visible 一行；权限点与角色授权不变（quality:lot:view）。

UPDATE sys_menu SET visible = '1' WHERE menu_id = 403 AND visible = '0';

SELECT menu_id, menu_name, visible FROM sys_menu WHERE menu_id = 403;
