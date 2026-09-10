-- 83_rebuild_sys_dept_org.sql
-- 按 Leo 2026-09-10 18:43 给出的新组织架构，TRUNCATE sys_dept 后重建
-- 前置 guard 备份：jjx-docs/sql/backups/sys_dept_rebuild_org_20260910-1848.sql
--
-- 新架构：
--   深圳市精捷信科技有限公司
--     总经理办公室
--       财务部 / 行政部 / 工程部 / 制造部 / 资材部(仓库、采购部) / 品管部 / 业务部 / 生产中心(刀模中心、冲型车间、加工车间、印刷车间)
--
-- 引用方（本库无 FK 指向 sys_dept，但需按名重映射，避免悬空 dept_id）：
--   sys_user.dept_id(42)、hr_employee.dept_id(52)、hr_dept_mapping.dept_id(13)
-- 旧→新 别名表（保守映射：只做同名或显然的改名，不臆造业务归属）：
--   JJX公司/深圳市精捷信科技有限公司/部门1 → 深圳市精捷信科技有限公司
--   办公室→行政部 ；研发部→工程部 ；市场部→业务部 ；品质部→品管部
--   印刷一组/印刷二组→印刷车间 ；冲型一组/冲型二组→冲型车间
--   组装车间/组装一组/组装二组→加工车间 ；生产中心→生产中心（原样保留，不臆造归属）
-- ⚠️ 注：原「生产中心」下的 19 名员工，其 Excel 原始部门文本（制造部/加工/刀模）在导入时已合并，
--    无法精确还原，故保留在「生产中心」，待确认后再分或重导。

-- ============ 1. 先抓取重映射关系（必须在 truncate 之前） ============
DROP TEMPORARY TABLE IF EXISTS tmp_dept_alias;
CREATE TEMPORARY TABLE tmp_dept_alias (
  old_name VARCHAR(64) NOT NULL,
  new_name VARCHAR(64) NOT NULL,
  PRIMARY KEY (old_name)
) ENGINE = MEMORY;
INSERT INTO tmp_dept_alias (old_name, new_name) VALUES
  ('JJX公司', '深圳市精捷信科技有限公司'),
  ('深圳市精捷信科技有限公司', '深圳市精捷信科技有限公司'),
  ('部门1', '深圳市精捷信科技有限公司'),
  ('总经理办公室', '总经理办公室'),
  ('财务部', '财务部'),
  ('行政部', '行政部'),
  ('办公室', '行政部'),
  ('工程部', '工程部'),
  ('研发部', '工程部'),
  ('制造部', '制造部'),
  ('资材部', '资材部'),
  ('仓库', '仓库'),
  ('采购部', '采购部'),
  ('品管部', '品管部'),
  ('品质部', '品管部'),
  ('业务部', '业务部'),
  ('市场部', '业务部'),
  ('生产中心', '生产中心'),
  ('刀模中心', '刀模中心'),
  ('刀模', '刀模中心'),
  ('冲型车间', '冲型车间'),
  ('冲型一组', '冲型车间'),
  ('冲型二组', '冲型车间'),
  ('冲型', '冲型车间'),
  ('加工车间', '加工车间'),
  ('加工', '加工车间'),
  ('印刷车间', '印刷车间'),
  ('印刷一组', '印刷车间'),
  ('印刷二组', '印刷车间'),
  ('印刷', '印刷车间'),
  ('组装车间', '加工车间'),
  ('组装一组', '加工车间'),
  ('组装二组', '加工车间'),
  ('组装', '加工车间');

DROP TEMPORARY TABLE IF EXISTS tmp_user_newdept;
CREATE TEMPORARY TABLE tmp_user_newdept AS
SELECT u.user_id, a.new_name AS new_dept_name
FROM sys_user u
LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
LEFT JOIN tmp_dept_alias a ON a.old_name = d.dept_name;

DROP TEMPORARY TABLE IF EXISTS tmp_emp_newdept;
CREATE TEMPORARY TABLE tmp_emp_newdept AS
SELECT e.emp_id, a.new_name AS new_dept_name
FROM hr_employee e
LEFT JOIN sys_dept d ON d.dept_id = e.dept_id
LEFT JOIN tmp_dept_alias a ON a.old_name = d.dept_name;

-- ============ 2. 重建部门树 ============
TRUNCATE TABLE sys_dept;

INSERT INTO sys_dept (dept_id, parent_id, dept_name, order_num, leader, leader_user_id, status, del_flag, create_by, create_time)
VALUES
  (1,  0,  '深圳市精捷信科技有限公司', 1, '林仪增', 134, '0', '0', 'dahuang', NOW()),
  (2,  1,  '总经理办公室',             1, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (3,  2,  '财务部',                   1, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (4,  2,  '行政部',                   2, '办公室管理员', 84, '0', '0', 'dahuang', NOW()),
  (5,  2,  '工程部',                   3, '工程业务员',   89, '0', '0', 'dahuang', NOW()),
  (6,  2,  '制造部',                   4, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (7,  2,  '资材部',                   5, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (8,  7,  '仓库',                     1, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (9,  7,  '采购部',                   2, '采购业务员',   91, '0', '0', 'dahuang', NOW()),
  (10, 2,  '品管部',                   6, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (11, 2,  '业务部',                   7, '销售业务员',   85, '0', '0', 'dahuang', NOW()),
  (12, 2,  '生产中心',                 8, '生产中心主任', 94, '0', '0', 'dahuang', NOW()),
  (13, 12, '刀模中心',                 1, NULL,     NULL, '0', '0', 'dahuang', NOW()),
  (14, 12, '冲型车间',                 2, '冲型车间主任', 96, '0', '0', 'dahuang', NOW()),
  (15, 12, '加工车间',                 3, '组装车间主任', 97, '0', '0', 'dahuang', NOW()),
  (16, 12, '印刷车间',                 4, '印刷车间主任', 95, '0', '0', 'dahuang', NOW());

-- ============ 3. 引用方重映射 ============
UPDATE sys_user u
JOIN tmp_user_newdept t ON t.user_id = u.user_id
JOIN sys_dept n ON n.dept_name = t.new_dept_name
SET u.dept_id = n.dept_id;

UPDATE sys_user SET dept_id = NULL WHERE dept_id IS NOT NULL AND dept_id NOT IN (SELECT dept_id FROM sys_dept);

UPDATE hr_employee e
JOIN tmp_emp_newdept t ON t.emp_id = e.emp_id
JOIN sys_dept n ON n.dept_name = t.new_dept_name
SET e.dept_id = n.dept_id;

UPDATE hr_employee SET dept_id = NULL WHERE dept_id IS NOT NULL AND dept_id NOT IN (SELECT dept_id FROM sys_dept);

-- ============ 4. 导入映射表按新部门重建 ============
DELETE FROM hr_dept_mapping;
INSERT INTO hr_dept_mapping (source_name, dept_id, remark, create_time)
SELECT s.src, d.dept_id, 'dev-20260910-011 新组织架构重建（83）', NOW()
FROM (
  SELECT '工程部' AS src, '工程部' AS dst
  UNION ALL SELECT '业务部', '业务部'
  UNION ALL SELECT '仓库',   '仓库'
  UNION ALL SELECT '资材部', '资材部'
  UNION ALL SELECT '品质部', '品管部'
  UNION ALL SELECT '品管部', '品管部'
  UNION ALL SELECT '部品管', '品管部'
  UNION ALL SELECT '制造部', '制造部'
  UNION ALL SELECT '加工',   '加工车间'
  UNION ALL SELECT '组装',   '加工车间'
  UNION ALL SELECT '刀模',   '刀模中心'
  UNION ALL SELECT '冲型',   '冲型车间'
  UNION ALL SELECT '印刷',   '印刷车间'
) s
JOIN sys_dept d ON d.dept_name = s.dst AND d.del_flag = '0';

-- ============ 5. 核验 ============
SELECT dept_id, parent_id, dept_name, order_num, leader_user_id FROM sys_dept ORDER BY dept_id;
SELECT 'sys_user 无部门' AS chk, COUNT(*) AS cnt FROM sys_user WHERE dept_id IS NULL
UNION ALL SELECT 'sys_user 悬空部门', COUNT(*) FROM sys_user WHERE dept_id IS NOT NULL AND dept_id NOT IN (SELECT dept_id FROM sys_dept)
UNION ALL SELECT 'hr_employee 无部门', COUNT(*) FROM hr_employee WHERE dept_id IS NULL
UNION ALL SELECT 'hr_employee 悬空部门', COUNT(*) FROM hr_employee WHERE dept_id IS NOT NULL AND dept_id NOT IN (SELECT dept_id FROM sys_dept)
UNION ALL SELECT 'hr_dept_mapping 行数', COUNT(*) FROM hr_dept_mapping;
