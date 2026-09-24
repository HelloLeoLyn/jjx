-- risk: medium
-- dev-20260924-004（报废线一期）：不良件级追溯（件 + 缺陷记录）与原因口径字段。
-- Tables: quality_ncr_piece, quality_ncr_piece_defect, quality_ncr, quality_ncr_action
-- 口径（方案 design/defect-piece-trace-reason-code-dev-20260924-004.md v3）：
--   ① 件表/缺陷记录表**只做身份与追溯**，不参与任何库存数量计算（库存仍只由 inventory_transaction 驱动）
--   ② 原因 = 检验项目（quality_lot_item.check_item）+ 不合格分级（CR/MA/MI）；不新建全局原因字典
--   ③ 一件可多项不合格（缺陷记录 件×项目×分级），件上保留主缺陷（CR>MA>MI，同级取第一个）
-- 幂等：CREATE TABLE IF NOT EXISTS + 列存在性判定的动态 ALTER（可重复执行）

CREATE TABLE IF NOT EXISTS `quality_ncr_piece` (
  `piece_id` bigint NOT NULL AUTO_INCREMENT,
  `piece_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '件号：<不良单号>-D<序号>',
  `ncr_id` bigint NOT NULL COMMENT '所属不良单',
  `lot_id` bigint DEFAULT NULL COMMENT '来源检验批',
  `seq_no` int NOT NULL COMMENT '序号（发号幂等键）',
  `main_check_item` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主缺陷检验项目',
  `main_defect_level` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主缺陷分级 CR/MA/MI',
  `actual_value` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '该件实测值（逐件实测按竖线拆出）',
  `sample_index` int DEFAULT NULL COMMENT '对应 sample_values 段序（1 起）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REWORKING/RECOVERED/SCRAPPED/CONCEDED/RETURNED/VOID',
  `dispose_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近处置类型',
  `action_id` bigint DEFAULT NULL COMMENT '关联处置单行',
  `order_id` bigint DEFAULT NULL COMMENT '生产工单ID（冗余追溯）',
  `work_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工单号（冗余追溯）',
  `lot_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验批号（冗余追溯）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`piece_id`),
  UNIQUE KEY `uk_piece_no` (`piece_no`),
  UNIQUE KEY `uk_ncr_seq` (`ncr_id`,`seq_no`),
  KEY `idx_ncr_status` (`ncr_id`,`status`),
  KEY `idx_action` (`action_id`),
  KEY `idx_main_defect` (`main_check_item`,`main_defect_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不良件（件级追溯，dev-20260924-004）';

CREATE TABLE IF NOT EXISTS `quality_ncr_piece_defect` (
  `defect_id` bigint NOT NULL AUTO_INCREMENT,
  `piece_id` bigint NOT NULL COMMENT '所属不良件',
  `ncr_id` bigint NOT NULL COMMENT '冗余：不良单',
  `lot_id` bigint DEFAULT NULL COMMENT '冗余：检验批',
  `check_item` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不合格检验项目',
  `defect_level` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不合格分级 CR/MA/MI（兜底其他可为空）',
  `is_main` tinyint NOT NULL DEFAULT '0' COMMENT '是否该件主缺陷',
  `sort_order` int NOT NULL DEFAULT '0',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`defect_id`),
  KEY `uk_piece_item_level` (`piece_id`,`check_item`,`defect_level`),
  KEY `idx_ncr_item_level` (`ncr_id`,`check_item`,`defect_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不良件缺陷记录（件×项目×分级，dev-20260924-004）';

-- quality_ncr：首因（台账列表按 项目×分级 筛选统计）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_ncr ADD COLUMN main_check_item varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''首因检验项目（dev-20260924-004）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_ncr' AND column_name = 'main_check_item');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_ncr ADD COLUMN main_defect_level varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''首因分级 CR/MA/MI（dev-20260924-004）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_ncr' AND column_name = 'main_defect_level');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- quality_ncr_action：本次处置的主缺陷 + 件数（与件表对账）
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_ncr_action ADD COLUMN main_check_item varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''本次处置主缺陷项目（dev-20260924-004）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_ncr_action' AND column_name = 'main_check_item');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_ncr_action ADD COLUMN main_defect_level varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''本次处置主缺陷分级（dev-20260924-004）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_ncr_action' AND column_name = 'main_defect_level');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE quality_ncr_action ADD COLUMN piece_count int NULL COMMENT ''本次处置件数（与 quality_ncr_piece 对账，dev-20260924-004）''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quality_ncr_action' AND column_name = 'piece_count');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
