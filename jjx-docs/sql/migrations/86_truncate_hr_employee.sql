-- dev-20260910-013：清空员工表，由用户重新导入
-- 用户指令（2026-09-10）："清空员工表，我来重新导入"
--
-- 破坏性语句说明（TRUNCATE）：
--   TRUNCATE hr_employee —— 清空 52 条员工记录（含自增重置）
-- 依赖情况：无外键指向 hr_employee（TRUNCATE 不会被拦）
-- 保留不动：hr_dept_mapping（导入时的"部门文本 → sys_dept"映射表，重新导入要靠它）
-- ⚠ 重新导入前请确认 hr_dept_mapping 覆盖新架构部门名：
--     当前缺 总经理办公室 / 财务部 / 行政部 / 采购部 / 生产中心
-- 执行前备份：scripts/db-migrate.sh 自动全库备份；另有表级 guard 备份 hr_employee_before-reimport_*.sql

TRUNCATE TABLE hr_employee;
