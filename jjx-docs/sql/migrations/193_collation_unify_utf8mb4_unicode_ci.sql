-- ============================================================================
-- 193_collation_unify_utf8mb4_unicode_ci.sql
-- 任务码：dev-20260921-046（任务 2108「跨表 collation 统一：字符串 JOIN 报 1267」）
--
-- 口径（2026-09-22 用户拍板）：全库字符串列统一到 utf8mb4_unicode_ci。
--   不采用 description 原稿建议的 utf8mb4_0900_ai_ci：库默认（information_schema.SCHEMATA）
--   与 65 张表已经是 utf8mb4_unicode_ci，统一到这一侧新表/新迁移不写 COLLATE 才会自动一致，
--   才根治复发；统一到 0900 等于把默认留在对岸（180_add_quality_capa.sql:22 就是显式写 0900
--   才又造出一批分裂）。
--
-- 问题（可现场复现）：
--   SELECT sop.product_code FROM sales_order_product sop
--     JOIN sales_quotation_item sqi ON sop.product_code = sqi.product_code LIMIT 1;
--   → ERROR 1267 (HY000): Illegal mix of collations (utf8mb4_unicode_ci,IMPLICIT)
--                          and (utf8mb4_0900_ai_ci,IMPLICIT) for operation '='
--   已在 jjx-docs/sql/migrations/168_sales_order_price_backfill.sql:26 用显式 COLLATE 绕过；
--   应用侧（jjx-server/src、jjx-web/src）0 处 COLLATE ⇒ 任何 Mapper 写出这类 JOIN 就是运行时 500。
--
-- 本迁移做的事（只改字符集/collation，不改结构、不动数据语义）：
--   ① 51 张表 CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
--      （50 张表默认即 0900_ai_ci + 1 张 sys_event_config_bak_20260814 表默认 unicode_ci 但含 8 个 0900_ai_ci 列）
--   ② 1 列 MODIFY：sys_menu.ancestors varchar(200)（utf8mb3_general_ci → utf8mb4_unicode_ci）
--   ③ 库默认兜底：仅当 SCHEMATA 默认不是 utf8mb4_unicode_ci 时才 ALTER DATABASE（当前已一致 → 空转）
--   视图 v_inventory_transaction / v_user_permissions 无自有字符集，跟随基表，无需处理。
--
-- 前置体检实测（2026-09-22，只读；脚本：scripts/check-collation.sh）：
--   · 规模：全库 BASE TABLE 21,204 行、DATA+INDEX 15.4MB（utf8mb4_unicode_ci 65 表 7.9MB /
--     utf8mb4_0900_ai_ci 50 表 7.5MB）⇒ 秒级，无需分批、无长锁风险；全部 InnoDB + Dynamic。
--   · 外键：字符串列上 0 个外键（information_schema.KEY_COLUMN_USAGE 实测 0 行）⇒ CONVERT TO 不被 FK 阻挡。
--   · 唯一索引：含串列的 UNIQUE 索引 67 个，按「索引全列 + COLLATE utf8mb4_unicode_ci」逐一分组比对
--     ⇒ 0 撞键（含 2 个复合键 inventory_item.uk_inventory_item_source、sys_tag_rel.uk_tag_rel 的复查）。
--     ⚠ 复检方法：必须按「索引全部列」分组并排除 NULL；只按单个串列 GROUP BY 会因复合键/多 NULL 假阳性。
--   · 生成列/触发器/存储过程：0 个（无 GENERATION_EXPRESSION）。
--   · 最大串列：sys_oper_log.oper_param mediumtext、若干 text —— utf8mb4 字节上限不变，无溢出风险。
--
-- 幂等：每条 ALTER 都先用 information_schema 判定「仍不是 utf8mb4_unicode_ci」才执行；
--       已统一的表再次执行只会打印 skip，不会重写表。
-- 执行通道（禁止直接 mysql < 本文件）：
--   bash scripts/db-migrate.sh 193_collation_unify_utf8mb4_unicode_ci.sql --yes --task dev-20260921-046
--   （入口内部会先做全库备份；本迁移属表结构变更 ⇒ 备份强制，不由人裁量）
-- 回滚：无自动回滚。本迁移只改字符集不改数据语义，需要回退时对同表 CONVERT 回
--       utf8mb4_0900_ai_ci（utf8mb3 列回退为 utf8mb3_general_ci）即可；亦可用入口做的全库备份恢复。
-- 体检脚本：bash scripts/check-collation.sh（咨询） / --strict（门禁） / --preflight（执行前预检）
-- 执行后校验（期望 0 / 0 / 0 / 0）见文件末尾 §校验。
-- ============================================================================
USE `jjx_erp_db`;

-- ── ① 表级统一（51 张）────────────────────────────────────────────────────
-- biz_requirement
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `biz_requirement` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip biz_requirement: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_requirement'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'biz_requirement'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- biz_requirement_approval
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `biz_requirement_approval` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip biz_requirement_approval: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_requirement_approval'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'biz_requirement_approval'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_base
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_base` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_base: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_base'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_base'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_die
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_die` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_die: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_die'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_die'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_film
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_film` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_film: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_film'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_film'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_resource_maintenance
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_resource_maintenance` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_resource_maintenance: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_resource_maintenance'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_resource_maintenance'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_resource_product_rel
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_resource_product_rel` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_resource_product_rel: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_resource_product_rel'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_resource_product_rel'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_screen_frame
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_screen_frame` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_screen_frame: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_screen_frame'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_screen_frame'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- engineering_screen_plate
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `engineering_screen_plate` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip engineering_screen_plate: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'engineering_screen_plate'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'engineering_screen_plate'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_disposition_order
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `inventory_iqc_disposition_order` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip inventory_iqc_disposition_order: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_iqc_disposition_order'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'inventory_iqc_disposition_order'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_quarantine
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `inventory_iqc_quarantine` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip inventory_iqc_quarantine: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_iqc_quarantine'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'inventory_iqc_quarantine'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_return_order
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `inventory_iqc_return_order` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip inventory_iqc_return_order: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_iqc_return_order'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'inventory_iqc_return_order'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_rework_order
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `inventory_iqc_rework_order` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip inventory_iqc_rework_order: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_iqc_rework_order'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'inventory_iqc_rework_order'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_scrap_order
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `inventory_iqc_scrap_order` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip inventory_iqc_scrap_order: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_iqc_scrap_order'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'inventory_iqc_scrap_order'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- production_task
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `production_task` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip production_task: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'production_task'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'production_task'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- production_task_event
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `production_task_event` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip production_task_event: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'production_task_event'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'production_task_event'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- production_work_report
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `production_work_report` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip production_work_report: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'production_work_report'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'production_work_report'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- quality_capa
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `quality_capa` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip quality_capa: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_capa'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'quality_capa'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- quality_lot
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `quality_lot` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip quality_lot: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_lot'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'quality_lot'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- quality_lot_item
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `quality_lot_item` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip quality_lot_item: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_lot_item'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'quality_lot_item'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- quality_ncr
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `quality_ncr` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip quality_ncr: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_ncr'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'quality_ncr'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- quality_ncr_action
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `quality_ncr_action` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip quality_ncr_action: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_ncr_action'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'quality_ncr_action'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- quality_sampling_plan
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `quality_sampling_plan` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip quality_sampling_plan: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_sampling_plan'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'quality_sampling_plan'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- review_flow
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `review_flow` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip review_flow: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_flow'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'review_flow'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_customer
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_customer` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_customer: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_customer'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_customer'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_delivery
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_delivery` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_delivery: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_delivery'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_delivery'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_inquiry
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_inquiry` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_inquiry: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_inquiry'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_inquiry'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_invoice
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_invoice` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_invoice: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_invoice'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_invoice'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_order
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_order` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_order: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_order'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_order_review
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_order_review` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_order_review: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order_review'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_order_review'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_order_stock_reserve
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_order_stock_reserve` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_order_stock_reserve: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order_stock_reserve'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_order_stock_reserve'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_quotation
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_quotation` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_quotation: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_quotation'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_quotation'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_quotation_flow
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_quotation_flow` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_quotation_flow: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_quotation_flow'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_quotation_flow'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_quotation_item
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_quotation_item` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_quotation_item: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_quotation_item'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_quotation_item'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_receipt
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_receipt` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_receipt: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_receipt'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_receipt'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_return
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_return` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_return: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_return'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_return'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_return_item
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_return_item` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_return_item: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_return_item'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_return_item'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_sample_bom
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_sample_bom` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_sample_bom: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_sample_bom'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_sample_bom'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_sample_order
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_sample_order` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_sample_order: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_sample_order'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_sample_order'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_sample_process
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_sample_process` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_sample_process: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_sample_process'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_sample_process'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_sample_round
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_sample_round` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_sample_round: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_sample_round'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_sample_round'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sales_sample_transfer
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sales_sample_transfer` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sales_sample_transfer: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_sample_transfer'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sales_sample_transfer'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_config
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_config` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_config: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_config'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_dict_item
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_dict_item` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_dict_item: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_item'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_dict_item'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_error_log
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_error_log` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_error_log: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_error_log'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_error_log'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_event_config
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_event_config` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_event_config: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_event_config'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_event_config'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_event_config_bak_20260814
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_event_config_bak_20260814` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_event_config_bak_20260814: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_event_config_bak_20260814'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_event_config_bak_20260814'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_login_log
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_login_log` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_login_log: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_login_log'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_notification
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_notification` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_notification: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notification'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_notification'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_number_sequence
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_number_sequence` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_number_sequence: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_number_sequence'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_number_sequence'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- sys_oper_log
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_oper_log` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT ''skip sys_oper_log: already utf8mb4_unicode_ci''')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log'
    AND (TABLE_COLLATION <> 'utf8mb4_unicode_ci' OR TABLE_COLLATION IS NULL
      OR EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                 WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'sys_oper_log'
                   AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci')));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ── ② 列级统一：utf8mb3 遗留列（1 列）────────────────────────────────────
-- sys_menu.ancestors varchar(200)：唯一一列 utf8mb3_general_ci；非索引列，扩到 utf8mb4 不影响键长
SET @s := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE `sys_menu` MODIFY COLUMN `ancestors` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''祖级列表''',
  'SELECT ''skip sys_menu.ancestors''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'ancestors'
    AND (COLLATION_NAME IS NULL OR COLLATION_NAME <> 'utf8mb4_unicode_ci'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ── ③ 库默认兜底（当前已一致 → 空转；防将来新库/新表漂移）────────────────
SET @s := (SELECT IF(COUNT(*) > 0,
  CONCAT('ALTER DATABASE `', DATABASE(), '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'),
  'SELECT ''skip database default: already utf8mb4_unicode_ci''')
  FROM information_schema.SCHEMATA
  WHERE SCHEMA_NAME = DATABASE() AND DEFAULT_COLLATION_NAME <> 'utf8mb4_unicode_ci');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ── 校验（§校验）：执行后应全部为 0 ──────────────────────────────────────
-- chk_cols_base  基表列仍非 utf8mb4_unicode_ci 的数量              → 期望 0
-- chk_tbls       基表默认仍非 utf8mb4_unicode_ci 的数量            → 期望 0
-- chk_db         库默认仍非 utf8mb4_unicode_ci                      → 期望 0
-- chk_cols_view  视图列仍报非 utf8mb4_unicode_ci（信息项，随基表）  → 期望 0
SELECT 'chk_cols_base' AS chk, COUNT(*) AS n
  FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
    ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
  WHERE c.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'BASE TABLE'
    AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci'
UNION ALL
SELECT 'chk_tbls', COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND TABLE_COLLATION <> 'utf8mb4_unicode_ci'
UNION ALL
SELECT 'chk_db', COUNT(*) FROM information_schema.SCHEMATA
  WHERE SCHEMA_NAME = DATABASE() AND DEFAULT_COLLATION_NAME <> 'utf8mb4_unicode_ci'
UNION ALL
SELECT 'chk_cols_view', COUNT(*) FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
    ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
  WHERE c.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'VIEW'
    AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci';

-- ── 执行记录 ─────────────────────────────────────────────────────────────
-- 2026-09-22 生成人 Hermes（任务 2108，生成时 DB 未执行）
-- 2026-09-22 11:32:26~11:32:28 实际执行：由另一并发会话以 thread_id=7959 直接跑本文件内容
--   （binlog 实证：51 条 CONVERT + sys_menu MODIFY，顺序与本文件一致；非 db-migrate.sh 通道
--    ⇒ 当时无前置全库备份、ops.schema.applied 未记 193）。
-- 2026-09-22 11:41:05 版本补记：bash scripts/db-migrate.sh --record 193 --yes --task dev-20260921-046
--   （Hermes；sys_config guard 备份 ~/jjx-backups/sys_config_record-version_20260922-1141.sql
--    md5 18956b949759b0ebac6b6710d8002860）→ ops.schema.applied = …,192,193,194，version=194。
-- 校验结果：chk_cols_base=0 / chk_tbls=0 / chk_db=0；chk_cols_view=1
--   （残留 v_inventory_transaction.transaction_type_name 仍 0900_ai_ci —— 视图表达式的 collation
--    不随基表转换，需重建视图；已另行记录，不影响本次收口。）
