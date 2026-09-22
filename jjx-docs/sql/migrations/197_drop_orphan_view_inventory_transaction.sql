-- ============================================================================
-- 197_drop_orphan_view_inventory_transaction.sql
-- 任务码：dev-20260921-046（任务 2108 的收尾小项；用户 2026-09-22 明确「删除」）
--
-- 【撞号改号说明】本文件原为 `196_`，与并发会话的 `196_fix_menu_icon_default_and_dirty.sql`
-- （任务 dev-20260922-006，用户 12:03「一起做」）撞号。按仓库惯例「后建者改号」（先例 ab3e9c48），
-- 本文件后建（birth 12:04:51 vs 对方 12:04:31）、且后执行（12:04:43 vs 对方 12:04:35），
-- 故改号为 197，**内容未改**。已应用事实不变：DROP VIEW 已于 12:04:43 执行；
-- 版本用 `bash scripts/db-migrate.sh --record 197 --yes --task dev-20260921-046` 补记
-- （登记后 applied = …,195,196,197，其中 196 = 图标修复、197 = 本文件）。
--
-- 目的：删除孤儿视图 `v_inventory_transaction`。
--
-- 为什么是孤儿（2026-09-22 核查，全部只读）：
--   · 全仓代码引用：0 —— jjx-server/src、jjx-web/src 里 grep v_inventory_transaction 无命中；
--   · 视图/存储过程依赖：0 —— information_schema.VIEWS 中无其它视图定义引用它，
--     information_schema.ROUTINES 命中 0；
--   · 仅有的文字提及是 jjx-docs/history/db-audit-report.md:77「视图无注释：v_inventory_transaction /
--     v_material_latest_inquiry / v_user_permissions 功能不明」——即历史审计也判定其用途不明。
--   注：它刚在 195 里被重建以清 collation 残留（transaction_type_name 0900_ai_ci → unicode_ci）；
--       本次直接删除，135 里积累的维护面随之消失。
--
-- 保留对照：同库另一视图 `v_user_permissions` 同样无注释，但**本迁移不动它**（用户只要求删这个）。
--
-- 风险与备份：破坏性语句（DROP），按 §2 属高风险 —— db-migrate.sh 会先做全库快照再执行。
-- 回滚：DROP 不可自动回滚；需要恢复时用 195_view_collation_rebuild.sql 文末「§原定义快照」重建
--       （命令：CREATE OR REPLACE DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW v_inventory_transaction AS ...），
--       或从本次前置全库快照里恢复视图定义。
-- 幂等：DROP VIEW IF EXISTS，可重复执行。
-- 执行通道：bash scripts/db-migrate.sh 196_drop_orphan_view_inventory_transaction.sql --yes --task dev-20260921-046
-- ============================================================================
USE `jjx_erp_db`;

-- ── ① 改前取证（视图存在 1 / 依赖 0 / 行数基线）──────────────────────────
SELECT 'before_view_exists' AS chk, COUNT(*) AS n
  FROM information_schema.VIEWS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'v_inventory_transaction'
UNION ALL
SELECT 'before_views_depending_on_it', COUNT(*)
  FROM information_schema.VIEWS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'v_inventory_transaction'
   AND VIEW_DEFINITION LIKE '%v_inventory_transaction%'
UNION ALL
SELECT 'before_base_tables', COUNT(*)
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';

-- ── ② 删除孤儿视图 ────────────────────────────────────────────────────────
-- 原因：全仓 0 引用、无视图/过程依赖、历史审计标注用途不明（见文件头）。
DROP VIEW IF EXISTS `v_inventory_transaction`;

-- ── ③ 校验：视图应消失；基表与 collation 不受影响 ─────────────────────────
SELECT 'after_view_exists_should_be_0' AS chk, COUNT(*) AS n
  FROM information_schema.VIEWS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'v_inventory_transaction'
UNION ALL
SELECT 'after_base_tables_should_be_114', COUNT(*)
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
UNION ALL
SELECT 'after_remaining_views', COUNT(*)
  FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE()
UNION ALL
SELECT 'chk_cols_non_unicode_ci', COUNT(*) FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
    ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
  WHERE c.TABLE_SCHEMA = DATABASE() AND c.COLLATION_NAME IS NOT NULL
    AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci'
UNION ALL
SELECT 'chk_tbls_non_unicode_ci', COUNT(*)
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
   AND TABLE_COLLATION <> 'utf8mb4_unicode_ci';

-- ── 执行记录 ─────────────────────────────────────────────────────────────
-- 2026-09-22 生成人 Hermes（任务 2108；生成时未执行）
-- 2026-09-22 12:04 执行：bash scripts/db-migrate.sh 196_drop_orphan_view_inventory_transaction.sql --yes --task dev-20260921-046
--   前置备份（schema 快照）：~/jjx-backups/jjx_schema_snapshot_20260922-1204_before-196.sql
--     md5 9c8808b003a95872335d2cb1a0a21344（已由入口自动登记 backup-index.tsv）
--   结果：v_inventory_transaction 已删除（remaining_views=1，仅 v_user_permissions）；基表仍 114 张；
--         chk_cols_non_unicode_ci=0 / chk_tbls_non_unicode_ci=0
--   版本：执行时记为 …,195,196；改号后经 --record 197 补记为 …,195,196,197
--         （196 = dev-20260922-006 图标修复，197 = 本文件；"196" 曾短暂指向本文件，已消除歧义）
--   回滚：见文件头（用 195 的 §原定义快照重建）
