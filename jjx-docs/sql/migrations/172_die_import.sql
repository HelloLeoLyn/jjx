-- ============================================================================
-- 172_die_import.sql
-- 任务码：dev-20260921-047（刀模 Excel 导入：模板 + 导入功能 + 老台账导入）
--
-- 1) engineering_die 补两列：数量(把) / 入库日期（老台账里有，表里没有）
-- 2) 新增刀模专用权限按钮「刀模导入」 engineering:die:import（挂在菜单 375 刀模管理 下），
--    并授权给当前拥有 375 的角色（超管1 / 工程全权限16 / 工程业务操作17 / 工程审核员18）
--
-- 幂等：列用 information_schema 判断；菜单按 perms 判断；授权按 (role_id,menu_id) 判断。
-- ============================================================================

SET @db := DATABASE();

-- 1) 数量(把)
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=@db AND table_name='engineering_die' AND column_name='quantity') = 0,
  'ALTER TABLE engineering_die ADD COLUMN quantity int DEFAULT NULL COMMENT ''数量(把)'' AFTER version',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 入库日期
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=@db AND table_name='engineering_die' AND column_name='stock_in_date') = 0,
  'ALTER TABLE engineering_die ADD COLUMN stock_in_date date DEFAULT NULL COMMENT ''入库日期'' AFTER location',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 刀模导入 权限按钮
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status,
                      perms, icon, create_by, create_time, remark)
SELECT '刀模导入', 375, 1, '', NULL, '1', '0', 'F', '0', '0',
       'engineering:die:import', '#', 'admin', NOW(), '刀模 Excel 导入（含下载模板）'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'engineering:die:import');

-- 4) 授权：给已拥有「刀模管理」(375) 的角色
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.perms = 'engineering:die:import'
 WHERE rm.menu_id = 375
   AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.menu_id);
