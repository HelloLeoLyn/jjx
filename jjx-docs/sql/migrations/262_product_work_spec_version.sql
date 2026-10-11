-- 262_product_work_spec_version.sql
-- dev-20261011-008（2026-10-11）：产品作业规范发布版本（§15 已登记 approvedNewTables）
-- 两张表：发布版本 + 发布条目。新建空表，无存量迁移。work_spec_usage 属第4段（工单绑定）。
-- 回滚：DROP TABLE product_work_spec_item; DROP TABLE product_work_spec_version;
CREATE TABLE IF NOT EXISTS `product_work_spec_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `version_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品内版本号，如 V1.0',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/RETIRED',
  `change_summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '本次修订说明',
  `based_on_version_id` bigint DEFAULT NULL COMMENT '基于哪个版本（可空）',
  `manifest_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布清单校验值',
  `effective_from` datetime DEFAULT NULL COMMENT '计划生效时间（可空）',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布人',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_version` (`product_id`,`version_no`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品作业规范发布版本';

CREATE TABLE IF NOT EXISTS `product_work_spec_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `spec_version_id` bigint NOT NULL COMMENT '所属发布版本ID',
  `resource_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPEC_JSON/BOM/ROUTING/DRAWING',
  `source_id` bigint DEFAULT NULL COMMENT '原始资料记录ID',
  `source_version_id` bigint DEFAULT NULL COMMENT '原始资料不可变版本ID（若支持）',
  `operation_ref` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '对应工序标识（若适用）',
  `snapshot_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '发布时完整快照/引用描述',
  `content_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容或文件 SHA-256',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_version` (`spec_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品作业规范发布条目';
