-- 259_sample_requisition_and_defect_tables.sql
-- dev-20261010-028（2026-10-10）：样品需求单 QR-065（挂样品单）+ 打样平台样品单级不良记录
-- §15.3 新表（用户 2026-10-10 拍板同意新建）：
--   1) sales_sample_requisition_sign  样品需求单会签记录（样品单级、三位签字、可多轮、纯留痕）
--   2) sales_sample_defect_record     打样不良原因及改善（样品单级、多条）
-- 幂等：CREATE TABLE IF NOT EXISTS。无存量数据迁移、不改现有表。
-- 回滚：DROP TABLE 两张。（对应 QR-065/权限回滚见 260 注释）

CREATE TABLE IF NOT EXISTS `sales_sample_requisition_sign` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_order_id` bigint NOT NULL COMMENT '样品单ID(sales_order.order_id)',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '轮次',
  `sign_role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '签字位: SALES业务/APPROVE核准/DEPT部门主管',
  `approve_result` tinyint NOT NULL DEFAULT '1' COMMENT '1同意/0不同意',
  `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '签字意见',
  `signer_id` bigint DEFAULT NULL COMMENT '签署人ID',
  `signer_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '签署人姓名',
  `sign_time` datetime DEFAULT NULL COMMENT '签署时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_role_round` (`sample_order_id`,`sign_role`,`round_no`),
  KEY `idx_order` (`sample_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='样品需求单会签记录';

CREATE TABLE IF NOT EXISTS `sales_sample_defect_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_order_id` bigint NOT NULL COMMENT '样品单ID(sales_order.order_id)',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '轮次',
  `craft_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '制样类别: PRINT印刷/PUNCH加工冲型',
  `defect_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不良原因',
  `improvement` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '改善措施',
  `recorder_id` bigint DEFAULT NULL COMMENT '记录人ID',
  `recorder_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '记录人姓名',
  `record_date` date DEFAULT NULL COMMENT '记录日期',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`sample_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打样不良原因及改善记录';
