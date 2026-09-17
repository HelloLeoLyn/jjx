-- ============================================================================
-- 119: 不良台账与处置 ——质量重构批1 / dev-20260917-003
-- 口径: 不良数量一律进台账（不写进允收入库数量）；处置只有 返工/让步接收/报废；
--       不良必须关联 工单 + 工序 + 检验批；台账结案时 不良数 = 各处置数量之和
-- ============================================================================
USE `jjx_erp_db`;

CREATE TABLE IF NOT EXISTS `quality_ncr` (
  `ncr_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '不良台账ID',
  `ncr_no` VARCHAR(50) NOT NULL COMMENT '不良单号',
  `lot_id` BIGINT NOT NULL COMMENT '检验批ID',
  `lot_type` VARCHAR(20) NOT NULL COMMENT 'IQC/IPQC/FQC',
  `order_id` BIGINT DEFAULT NULL COMMENT '生产工单ID（成品/过程）',
  `execution_id` BIGINT DEFAULT NULL COMMENT '工序执行ID',
  `material_id` BIGINT DEFAULT NULL,
  `material_code` VARCHAR(50) DEFAULT NULL,
  `material_name` VARCHAR(200) DEFAULT NULL,
  `product_id` BIGINT DEFAULT NULL,
  `product_code` VARCHAR(50) DEFAULT NULL,
  `product_name` VARCHAR(200) DEFAULT NULL,
  `batch_no` VARCHAR(100) DEFAULT NULL,
  `defect_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '不良数量',
  `cr_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '致命缺点数',
  `ma_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '主要缺点数',
  `mi_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '次要缺点数',
  `defect_reason` VARCHAR(500) DEFAULT NULL COMMENT '不良原因/描述',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处置/DISPOSING处置中/CLOSED已结',
  `disposed_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已处置数量',
  `scrapped_amount` DECIMAL(14,2) DEFAULT NULL COMMENT '报废金额（预留，口径A暂不启用）',
  `inspector` VARCHAR(64) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`ncr_id`),
  UNIQUE KEY `uk_quality_ncr_no` (`ncr_no`),
  KEY `idx_ncr_lot` (`lot_id`),
  KEY `idx_ncr_order` (`order_id`,`execution_id`),
  KEY `idx_ncr_status` (`status`,`lot_type`),
  KEY `idx_ncr_material` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不良台账';

CREATE TABLE IF NOT EXISTS `quality_ncr_action` (
  `action_id` BIGINT NOT NULL AUTO_INCREMENT,
  `ncr_id` BIGINT NOT NULL COMMENT '不良台账ID',
  `action_type` VARCHAR(20) NOT NULL COMMENT 'REWORK返工/CONCESSION让步接收/SCRAP报废',
  `quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '处置数量',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待执行/PROCESSING执行中/DONE已完成',
  `rework_execution_id` BIGINT DEFAULT NULL COMMENT '返工生成的工序执行ID',
  `customer_confirmed` TINYINT NOT NULL DEFAULT 0 COMMENT '客户是否确认（让步接收用）',
  `customer_confirm_time` DATETIME DEFAULT NULL,
  `approved_by` VARCHAR(64) DEFAULT NULL COMMENT '审批人',
  `approved_time` DATETIME DEFAULT NULL,
  `result_remark` VARCHAR(500) DEFAULT NULL,
  `operator_name` VARCHAR(64) DEFAULT NULL,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`action_id`),
  KEY `idx_ncr_action_ncr` (`ncr_id`),
  KEY `idx_ncr_action_type` (`action_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不良处置单（返工/让步接收/报废）';

SELECT '119 不良台账与处置表就绪' AS check_point;
