-- risk: high
-- task: dev-20260929-020
-- 标准工序加「系统内置」标记 + 初始化「作业说明返修（不指定工序）」内置工序。
-- 背景（history/... 与 sys_task dev-20260929-020）：成品返工处置原下拉是「标准工序全量（48 条）」，
--   用户诉求：① 工序应取自该成品当前生效工艺路线；② 有些返工确定不了具体工序（内容以作业说明为准）。
-- 口径（用户 2026-09-29 拍板 v3）：不把工序置空，而是提供一条内置工序承载"凭作业说明返修"——
--   既满足诉求，又保持 派工 → 报工 → 报工审批自动建返工复检批 的整条链不变。
-- 保护：is_system=1 的行在标准工序档案页禁编辑/禁删除（防误删导致返工断链）。
ALTER TABLE engineering_standard_process
    ADD COLUMN is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '系统内置：1=内置（禁编辑/禁删除），0=普通' AFTER is_final_process;

INSERT INTO engineering_standard_process
    (process_code, process_name, process_type, process_category, standard_labor_hours, standard_machine_hours,
     description, has_index, has_work_instruction, is_enabled, display_order, is_final_process, is_system, create_by)
SELECT 'REWORK_BY_NOTE', '作业说明返修（不指定工序）', 'OTHER', 'OTHER', NULL, NULL,
       '凭作业说明返修的返工项：不指定具体工序，返工内容以处置时填写的「返工要求」为准（返工工序下拉的内置项）',
       0, 0, 1, 9999, 0, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM engineering_standard_process WHERE process_code = 'REWORK_BY_NOTE');
