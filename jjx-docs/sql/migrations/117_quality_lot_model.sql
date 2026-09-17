-- ============================================================================
-- 117: 质量管理重构——检验批模型（quality_lot + quality_lot_item）
-- 任务码: dev-20260917-001
-- 口径（2026-09-17 用户定）：一笔报工 = 一个检验批；支持分批；必须能算 批量=已检+待检；
--   不良进不良台账（003）；复检=同批新版本（007）；成品全检可分批；来料按抽样方案（002）。
-- 说明: 新建表，幂等（IF NOT EXISTS），不迁移存量数据（013 统一重置）。
-- ============================================================================
USE `jjx_erp_db`;

CREATE TABLE IF NOT EXISTS `quality_lot` (
  `lot_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '检验批ID',
  `lot_no` VARCHAR(50) NOT NULL COMMENT '检验批号',
  `lot_type` VARCHAR(20) NOT NULL COMMENT '类型：IQC来料/IPQC过程/FQC成品',
  `source_type` VARCHAR(30) DEFAULT NULL COMMENT '来源类型：INBOUND_ITEM收货行/WORK_REPORT报工批/EXECUTION工序',
  `source_id` BIGINT DEFAULT NULL COMMENT '来源单据ID（入库单/工单/工序）',
  `source_item_id` BIGINT DEFAULT NULL COMMENT '来源行ID（入库行/报工ID/工序ID）',
  `order_id` BIGINT DEFAULT NULL COMMENT '生产工单ID（成品/过程）',
  `execution_id` BIGINT DEFAULT NULL COMMENT '工序执行ID',
  `material_id` BIGINT DEFAULT NULL COMMENT '物料ID（来料）',
  `material_code` VARCHAR(50) DEFAULT NULL,
  `material_name` VARCHAR(200) DEFAULT NULL,
  `product_id` BIGINT DEFAULT NULL COMMENT '产品ID（成品）',
  `product_code` VARCHAR(50) DEFAULT NULL,
  `product_name` VARCHAR(200) DEFAULT NULL,
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次/生产批号',
  `lot_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '批量（本批应检总量）',
  `inspected_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已检数量',
  `pass_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '合格数量',
  `fail_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '不良数量',
  `stored_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已入库/已放行数量（防超入）',
  `disposed_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已处置不良数量（返工+让步+报废）',
  `sampling_plan_id` BIGINT DEFAULT NULL COMMENT '抽样方案ID（来料）',
  `sample_quantity` DECIMAL(18,4) DEFAULT NULL COMMENT '抽样数量',
  `accept_number` DECIMAL(18,4) DEFAULT NULL COMMENT '允收数 AC',
  `reject_number` DECIMAL(18,4) DEFAULT NULL COMMENT '拒收数 RE',
  `result` VARCHAR(20) DEFAULT NULL COMMENT '判定：pending/pass/fail/concession',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待检/INSPECTING检验中/JUDGED已判定/CLOSED已关闭',
  `parent_lot_id` BIGINT DEFAULT NULL COMMENT '复检来源批（复检=同批新版本）',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号（复检递增）',
  `inspector` VARCHAR(64) DEFAULT NULL COMMENT '检验员',
  `inspect_time` DATETIME DEFAULT NULL COMMENT '检验时间',
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`lot_id`),
  UNIQUE KEY `uk_quality_lot_no` (`lot_no`),
  KEY `idx_quality_lot_source` (`source_type`,`source_id`),
  KEY `idx_quality_lot_order` (`order_id`,`execution_id`),
  KEY `idx_quality_lot_material` (`material_code`),
  KEY `idx_quality_lot_status` (`lot_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验批（IQC/IPQC/FQC 统一模型）';

CREATE TABLE IF NOT EXISTS `quality_lot_item` (
  `item_id` BIGINT NOT NULL AUTO_INCREMENT,
  `lot_id` BIGINT NOT NULL COMMENT '检验批ID',
  `check_item` VARCHAR(100) NOT NULL COMMENT '检验项目',
  `standard` VARCHAR(500) DEFAULT NULL COMMENT '检验规范/标准',
  `inspection_method` VARCHAR(100) DEFAULT NULL COMMENT '检验方法',
  `equipment` VARCHAR(100) DEFAULT NULL COMMENT '设备/量具',
  `sample_values` VARCHAR(500) DEFAULT NULL COMMENT '逐件实测值（| 分隔，供 SAMPLE1..n）',
  `actual_value` VARCHAR(200) DEFAULT NULL COMMENT '实测值（汇总/兼容）',
  `cr_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'CR 致命缺点数',
  `ma_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'MA 主要缺点数',
  `mi_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'MI 次要缺点数',
  `result` VARCHAR(20) DEFAULT NULL COMMENT '结论：pass/fail/pending',
  `remark` VARCHAR(500) DEFAULT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_id`),
  KEY `idx_quality_lot_item_lot` (`lot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验批检测项目结果';

SELECT '117 检验批模型建表完成' AS check_point;
SHOW COLUMNS FROM quality_lot;
