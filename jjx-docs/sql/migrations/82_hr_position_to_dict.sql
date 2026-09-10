-- 82_hr_position_to_dict.sql
-- 按 Leo 2026-09-10 18:22 要求：删除 hr_position 表，岗位改为字典（去重 12 项）
-- 员工岗位字段由 position_id(BIGINT→hr_position) 改为 position(VARCHAR→字典 hr_position 的 item_key)
-- 前置备份：jjx-docs/sql/backups/hr_position_to_dict_20260910-1822.sql
-- ⚠️ 执行后旧代码仍查 hr_position/position_id 列 → 必须重启后端 8080 才恢复

-- ============ 1. 字典 hr_position ============
INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active, create_time, deleted, tenant_id)
SELECT 'hr_position', '岗位', 'hr', 'dev-20260910-011：原 hr_position 表改字典（Leo 去重口径）', 0, 1, NOW(), 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'hr_position' AND deleted = 0);

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, create_time, deleted, tenant_id)
SELECT 'hr_position', s.k, s.k, s.k, s.so, 1, NOW(), 0, 1
FROM (
  SELECT '主管' AS k, 1 AS so
  UNION ALL SELECT '经理', 2
  UNION ALL SELECT '工程师', 3
  UNION ALL SELECT '业务', 4
  UNION ALL SELECT '助理', 5
  UNION ALL SELECT '组长', 6
  UNION ALL SELECT '印刷师傅', 7
  UNION ALL SELECT '网版师傅', 8
  UNION ALL SELECT '作业员', 9
  UNION ALL SELECT '冲型师傅', 10
  UNION ALL SELECT 'QC', 11
  UNION ALL SELECT 'IPQC', 12
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i
                  WHERE i.dict_code = 'hr_position' AND i.item_key = s.k AND i.deleted = 0);

-- ============ 2. hr_employee.position（幂等 ADD COLUMN） ============
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_employee' AND COLUMN_NAME = 'position');
SET @sql := IF(@has_col = 0,
  'ALTER TABLE hr_employee ADD COLUMN position VARCHAR(64) NULL COMMENT ''岗位（字典 hr_position）'' AFTER dept_id',
  'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- ============ 3. 旧数据迁移（position_id → position，去掉“QC 质检员”这类后缀） ============
UPDATE hr_employee e
JOIN hr_position p ON p.position_id = e.position_id
SET e.position = SUBSTRING_INDEX(p.position_name, ' ', 1)
WHERE e.position_id IS NOT NULL
  AND (e.position IS NULL OR e.position = '');

-- ============ 4. 删列 + 删表（幂等） ============
SET @has_pid := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hr_employee' AND COLUMN_NAME = 'position_id');
SET @sql := IF(@has_pid = 1, 'ALTER TABLE hr_employee DROP COLUMN position_id', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

DROP TABLE IF EXISTS hr_position;

-- ============ 5. 下线「岗位维护」菜单（346/347 中只删 347 + 其按钮 354） ============
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms IN ('hr:position:view','hr:position:edit'));
DELETE FROM sys_menu WHERE perms IN ('hr:position:view', 'hr:position:edit');

-- ============ 6. 表注释同步 ============
ALTER TABLE hr_employee COMMENT = '员工档案（人事主数据；账号可选关联；岗位走字典 hr_position）';
