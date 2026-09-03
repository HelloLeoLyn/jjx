-- 52_sys_task_test_cases.sql
-- 任务↔TC 关联进看板（任务1320，2026-09-03）
-- sys_task 加 test_cases：验收用例 TC 列表（逗号分隔，如 "TC-588,TC-591"）
ALTER TABLE sys_task ADD COLUMN test_cases varchar(255) NULL COMMENT '验收用例TC列表（逗号分隔）' AFTER remark;
