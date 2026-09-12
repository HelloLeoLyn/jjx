-- dev-20260912-015
-- 补齐 EngineeringFilm 实体及审核/下发业务已使用、但历史建表遗漏的字段。
-- 执行记录：2026-09-12 22:35 Codex；备份 md5=e7dbf355fce51df8c84b8b66fc1a56cd；ops.schema.version=105。
ALTER TABLE engineering_film
  ADD COLUMN approver_id BIGINT NULL COMMENT '审核人ID' AFTER approve_status,
  ADD COLUMN approver_name VARCHAR(100) NULL COMMENT '审核人姓名' AFTER approver_id,
  ADD COLUMN approve_time DATETIME NULL COMMENT '审核时间' AFTER approver_name,
  ADD COLUMN approve_remark VARCHAR(500) NULL COMMENT '审核意见' AFTER approve_time,
  ADD COLUMN release_by VARCHAR(64) NULL COMMENT '下发人' AFTER release_time;

INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by,create_time,description)
SELECT 'dev-20260912-015','DEV','dev','修复菲林审核字段缺失',0,'P1','Codex',NOW(),
       '补齐 engineering_film 的 approver_id、approver_name、approve_time、approve_remark、release_by 字段，并同步初始化建表结构。'
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE task_code='dev-20260912-015');
