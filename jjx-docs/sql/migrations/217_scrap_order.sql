-- risk: medium
-- dev-20260924-006（报废线三期）：成品报废单（照来料 inventory_iqc_scrap_order 形态）+ 菜单 + 单号规则。
-- Tables: quality_scrap_order, sys_menu, sys_role_menu, sys_config
-- 口径：报废单是**成品报废的实物凭据**（谁报废、报废多少、哪几件、谁批的）；库存不受影响（口径A：不良品从未进良品库）。
-- 幂等：CREATE TABLE IF NOT EXISTS + 存在性判定插入。

CREATE TABLE IF NOT EXISTS `quality_scrap_order` (
  `scrap_id` bigint NOT NULL AUTO_INCREMENT,
  `scrap_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报废单号：SCR+yyMMdd+3位',
  `action_id` bigint NOT NULL COMMENT '来源处置单（幂等键：一笔报废只出一张单）',
  `ncr_id` bigint NOT NULL COMMENT '不良单',
  `ncr_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lot_id` bigint DEFAULT NULL,
  `lot_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` decimal(14,2) NOT NULL COMMENT '报废数量',
  `defect_item` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主缺陷检验项目（原因口径）',
  `defect_level` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_range` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '件号区间（件级追溯）',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报废原因说明',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DONE' COMMENT 'DONE 已生效 / VOID 已撤销',
  `applicant` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交人',
  `approver` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人（超阈值报废才有）',
  `approve_time` datetime DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`scrap_id`),
  UNIQUE KEY `uk_scrap_no` (`scrap_no`),
  UNIQUE KEY `uk_scrap_action` (`action_id`),
  KEY `idx_scrap_ncr` (`ncr_id`),
  KEY `idx_scrap_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成品报废单（dev-20260924-006）';

-- 菜单：质量管理 → 成品报废单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth,
                      redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '成品报废单', 338, 8, '/quality/scrap-order', 'views/quality/scrap-order/index.vue', NULL, 1, 0,
       'C', 0, 0, 'quality:scrap-order:view', 'DeleteFilled', '0,338', NULL, 1, NULL, 0,
       'Hermes', NOW(), 'Hermes', NOW(), 'dev-20260924-006'
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE perms = 'quality:scrap-order:view') existing
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 34) r
JOIN (SELECT menu_id FROM sys_menu WHERE perms = 'quality:scrap-order:view' LIMIT 1) m
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- 单号规则
INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
SELECT 'biz_no_rule.quality_scrap',
       '{"prefix":"SCR","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',
       '成品报废单号规则', 'biz', 'dev-20260924-006：SCR+yyMMdd+3位，按日重置', 0, 1
FROM (SELECT 1) dummy
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT config_key FROM sys_config WHERE config_key = 'biz_no_rule.quality_scrap') existing
);
