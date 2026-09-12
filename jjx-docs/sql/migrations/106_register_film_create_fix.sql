-- dev-20260912-016
-- 登记菲林新增流程修复任务；本迁移不改业务表结构和数据。
-- 执行记录：2026-09-12 22:47 Codex；备份 md5=18589b676bea913758573e2dd23c99f5；ops.schema.version=106。
INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by,create_time,description)
SELECT 'dev-20260912-016','DEV','dev','修复菲林新增产品选择流程',0,'P1','Codex',NOW(),
       '新增菲林不再依赖列表顶部产品筛选；产品改为表单必填项，支持默认带入筛选产品并提供明确校验。'
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE task_code='dev-20260912-016');
