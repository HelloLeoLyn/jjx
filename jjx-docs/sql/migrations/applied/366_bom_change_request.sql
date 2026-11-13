-- 365 BOM 变更单 ECN-lite（dev-20261112-009）
-- 一张变更单打包多层 BOM：一次提交、一次审批、统一生效。
-- 不修改 approve_status 语义；不写库存/领料/成本。
-- 回退：按当天全库备份恢复。

CREATE TABLE IF NOT EXISTS bom_change_request (
  change_id bigint NOT NULL AUTO_INCREMENT COMMENT '变更单ID',
  change_no varchar(50) NOT NULL COMMENT '变更号',
  title varchar(200) NOT NULL COMMENT '标题',
  effective_date date NULL COMMENT '计划生效日期',
  status varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/EFFECTIVE/CANCELLED',
  created_by varchar(64) NULL,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_by varchar(64) NULL,
  approved_time datetime NULL,
  activated_by varchar(64) NULL,
  activated_time datetime NULL,
  remark varchar(500) NULL,
  PRIMARY KEY (change_id),
  UNIQUE KEY uk_bom_change_no (change_no),
  KEY idx_bom_change_status (status, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM变更单（ECN-lite）';

CREATE TABLE IF NOT EXISTS bom_change_request_item (
  item_id bigint NOT NULL AUTO_INCREMENT,
  change_id bigint NOT NULL,
  bom_id bigint NOT NULL,
  bom_version varchar(20) NULL COMMENT '变更单记录的BOM版本快照',
  target_effective_date date NULL COMMENT '本行生效日（空则取单头）',
  validate_result text NULL COMMENT '提交时逐张校验结果快照',
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (item_id),
  UNIQUE KEY uk_bom_change_item (change_id, bom_id),
  KEY idx_bom_change_item_change (change_id),
  KEY idx_bom_change_item_bom (bom_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM变更单明细';
