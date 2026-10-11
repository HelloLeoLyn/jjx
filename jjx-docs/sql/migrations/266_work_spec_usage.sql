-- 266_work_spec_usage.sql
-- dev-20261011-013（2026-10-11）：工单换版执行区间记录（产品作业规范版本追溯 P1 · 第4段-2）
-- 依据设计稿 jjx-docs/history/product-work-spec-p1-implementation-dev-20261011-004.md §2.3
-- §15 已登记：scripts/model-baseline.json approvedNewTables（dev-20261011-006）
-- risk: low（仅新建空表，无存量数据迁移；不改动现有表结构）
-- 回滚：DROP TABLE work_spec_usage;
-- 用途：一张生产工单分段执行多个发布版本时，逐段记录实际执行依据；
--      当前执行版本仍在 production_order.work_spec_version_id，历史段落留本表（禁止用覆盖工单字段代替履历）。
CREATE TABLE IF NOT EXISTS `work_spec_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_order_id` bigint NOT NULL COMMENT '生产工单ID（production_order.order_id）',
  `spec_version_id` bigint NOT NULL COMMENT '该区间实际使用的作业规范发布版本ID（product_work_spec_version.id）',
  `qty_from` decimal(18,4) DEFAULT NULL COMMENT '生效数量区间-起（含）；NULL 表示不限',
  `qty_to` decimal(18,4) DEFAULT NULL COMMENT '生效数量区间-止（含）；NULL 表示不限',
  `start_time` datetime DEFAULT NULL COMMENT '生效开始时间；NULL 表示不限',
  `end_time` datetime DEFAULT NULL COMMENT '生效结束时间；NULL 表示不限',
  `change_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '换版原因',
  `approved_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准人（显示名）',
  `approved_at` datetime DEFAULT NULL COMMENT '批准时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登记人账号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常/1删除',
  PRIMARY KEY (`id`),
  KEY `idx_work_order` (`work_order_id`),
  KEY `idx_spec_version` (`spec_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单换版执行区间记录';
