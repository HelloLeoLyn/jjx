-- dev-20260918-008：供应商返工复检子批次与批次谱系
CREATE TABLE IF NOT EXISTS `inventory_iqc_batch` (
  `batch_id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint DEFAULT NULL,
  `batch_no` varchar(100) NOT NULL,
  `parent_batch_id` bigint DEFAULT NULL,
  `parent_batch_no` varchar(100) DEFAULT NULL,
  `root_batch_no` varchar(100) NOT NULL,
  `batch_type` varchar(32) NOT NULL COMMENT 'ORIGINAL/REWORK/REINSPECTION',
  `source_rework_id` bigint DEFAULT NULL,
  `source_inbound_item_id` bigint DEFAULT NULL,
  `source_inspection_id` bigint DEFAULT NULL,
  `quantity` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_REINSPECTION',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_iqc_batch_material_no` (`material_id`,`batch_no`),
  KEY `idx_iqc_batch_parent` (`parent_batch_id`),
  KEY `idx_iqc_batch_rework` (`source_rework_id`),
  KEY `idx_iqc_batch_item` (`source_inbound_item_id`),
  CONSTRAINT `fk_iqc_batch_parent` FOREIGN KEY (`parent_batch_id`) REFERENCES `inventory_iqc_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IQC批次谱系';

ALTER TABLE `inventory_iqc_rework_order`
  ADD COLUMN `child_batch_id` bigint DEFAULT NULL AFTER `batch_no`,
  ADD COLUMN `child_batch_no` varchar(100) DEFAULT NULL AFTER `child_batch_id`,
  ADD KEY `idx_iqc_rework_child_batch` (`child_batch_id`);
