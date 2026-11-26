-- 391_grant_print_resources_menu.sql
-- 任务码：dev-20261125-013
-- 目的：把「网版作业」(menu 441) 授权给与「网版管理」(menu 374) 相同的角色。
-- 说明：本 SQL 已在正式库手工执行（7 行），此文件用于记账/归档，重复执行幂等（0 行）。
-- 回滚：DELETE FROM sys_role_menu WHERE menu_id=441;

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 441
FROM sys_role_menu r
WHERE r.menu_id = 374
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.menu_id = 441 AND x.role_id = r.role_id);

COMMIT;
