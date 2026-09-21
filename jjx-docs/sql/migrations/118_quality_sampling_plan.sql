-- ============================================================================
-- 118: 抽样方案（AQL）——质量重构批1 / dev-20260917-002
-- 用途: 来料检验建批时按"批量区间"自动带出 抽样数量 / AC / RE；报告 QR-037 的 AQL、AC、RE 栏取此配置
-- 口径: 仅来料使用（成品全检）；按 lot_type + aql_value + 批量区间匹配
-- ============================================================================
USE `jjx_erp_db`;

CREATE TABLE IF NOT EXISTS `quality_sampling_plan` (
  `plan_id` BIGINT NOT NULL AUTO_INCREMENT,
  `plan_name` VARCHAR(100) NOT NULL COMMENT '方案名（如 AQL1.0 一般检验II级）',
  `lot_type` VARCHAR(20) NOT NULL DEFAULT 'IQC' COMMENT '适用类型：IQC/FQC/ALL',
  `aql_value` DECIMAL(8,3) NOT NULL DEFAULT 1.000 COMMENT 'AQL 值',
  `inspection_level` VARCHAR(20) DEFAULT 'II' COMMENT '检验水平（I/II/III）',
  `lot_min` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '批量下限（含）',
  `lot_max` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '批量上限（含）',
  `sample_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '样本量 n',
  `accept_number` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '允收数 AC',
  `reject_number` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '拒收数 RE',
  `is_enabled` TINYINT NOT NULL DEFAULT 1,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`plan_id`),
  KEY `idx_sampling_match` (`lot_type`,`aql_value`,`lot_min`,`lot_max`,`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量抽样方案（AQL）';

SELECT '118 抽样方案表就绪' AS check_point;
