-- risk: low
-- dev-20261009-032：修复「新增物料编码从 1 开始」——把物料流水序列接续到存量最大流水 1601。
-- 背景：sys_number_sequence(material/GLOBAL) 于 2026-10-09 新建时未按存量初始化（current=2），
--       生成 RM000001/RM000002 与存量物料 RM000001..RM001601 撞号 → 新增报「物料编码已存在」。
-- 存量：inventory_material 已有 RM000001..RM001601（共 1601 条，含 AUX 混编于同一全局流水）。
-- 幂等：仅当 current_value < 1601 时上调。
UPDATE sys_number_sequence SET current_value = 1601
 WHERE sequence_key = 'material' AND current_value < 1601;
SELECT sequence_key, period_key, current_value FROM sys_number_sequence WHERE sequence_key = 'material';
