-- dev-20261021-018：全公司标准工序使用量聚合（只交付脚本，不执行）。
-- risk: low
-- 执行前必须由用户手工备份（排除 hr_employee）；agent 不生成备份。
-- 用户备份后通过 scripts/db-migrate.sh 应用；上线前由用户按建表闸登记表数基线。
-- period_date = engineering_bom.create_time 的日期；同一工序/日 COUNT(DISTINCT bom_id)。
-- 每日 03:00 事务内重算近 90 天，接口 SUM 日桶；禁止累计每日滚动90天快照（会重复计数）。
CREATE TABLE IF NOT EXISTS engineering_standard_process_usage (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_id BIGINT NOT NULL COMMENT '标准工序ID',
    period_date DATE NOT NULL COMMENT 'BOM创建日期（日桶）',
    use_count BIGINT NOT NULL DEFAULT 0 COMMENT '当日创建BOM引用数，按bom_id去重',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '聚合更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_period (process_id, period_date),
    KEY idx_period_date (period_date)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='全公司标准工序BOM使用量日聚合';
