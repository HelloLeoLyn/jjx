-- 201_widen_sys_config_value.sql
-- 备份人: dahuang（收尾任务 dev-20260922-024）
-- 原因: sys_config.config_value varchar(500) 已装不下 ops.schema.applied 集合（实测 498/500），
--       记账时追加迁移号触发 ERROR 1406「Data too long for column 'config_value'」，
--       而 scripts/db-migrate.sh 的 record_applied 用 2>/dev/null 吞掉错误、只回显本地拼出的集合，
--       导致「记账静默失败但脚本报成功」——2026-09-22 19:19 登记 200 时实际踩到。
-- 风险: low（仅加长 varchar(500)→varchar(2000)，不触碰任何行数据；500 与 2000 均为 2 字节长度前缀，可 INPLACE）
-- 依据: CONVENTIONS §2 备份分级（低风险→表级 guard 备份，由 db-migrate.sh 自动执行）
-- 涉及表: sys_config
-- risk: low
USE `jjx_erp_db`;

ALTER TABLE sys_config
  MODIFY COLUMN config_value varchar(2000) NOT NULL COMMENT '配置值';

-- ── 核验 ──────────────────────────────────────────────────────────────────
SELECT column_type AS config_value_type
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND column_name = 'config_value';
SELECT LENGTH(config_value) AS applied_len, config_value
  FROM sys_config WHERE config_key = 'ops.schema.applied';
