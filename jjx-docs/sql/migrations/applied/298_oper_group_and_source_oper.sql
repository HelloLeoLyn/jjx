-- dev-20261022-002：操作组号与附件引用来源操作主键。
-- risk: low
-- 只交付脚本，不由 agent 执行；执行前由用户手工备份（排除 hr_employee）。
-- 历史数据保留 NULL，不追溯回填；须先应用迁移再部署依赖新列的后端。
ALTER TABLE sys_oper_log
    ADD COLUMN oper_group_id VARCHAR(64) NULL COMMENT '同一次业务动作关联号',
    ADD INDEX idx_oper_group_id (oper_group_id);

ALTER TABLE sys_attachment_ref
    ADD COLUMN source_oper_id BIGINT NULL COMMENT '建立引用的操作日志主键',
    ADD INDEX idx_source_oper_id (source_oper_id);
