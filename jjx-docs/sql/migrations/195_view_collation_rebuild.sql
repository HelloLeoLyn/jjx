-- ============================================================================
-- 195_view_collation_rebuild.sql
-- 任务码：dev-20260921-046（任务 2108，193 的收尾小项）
--
-- 目的：清掉 193（全库字符串列统一 utf8mb4_unicode_ci）之后唯一的残留——
--       视图 v_inventory_transaction.transaction_type_name 仍为 utf8mb4_0900_ai_ci，
--       原因是视图里 CASE 表达式的中文字面量在「建视图那一刻」按当时连接的
--       collation_connection 定型（MySQL 8 对 utf8mb4 的默认 collation 就是
--       utf8mb4_0900_ai_ci），此后**不随基表转换而改变**。
--
-- 复现（只读）：
--   SELECT @@collation_connection;                     -- utf8mb4_0900_ai_ci
--   SELECT COLLATION((case 'inbound' when 'inbound' then '入库' end));            -- ai_ci（现状）
--   SELECT COLLATION((case 'inbound' when 'inbound' then '入库' end) COLLATE utf8mb4_unicode_ci); -- unicode_ci（修法）
--
-- 做法（幂等：CREATE OR REPLACE VIEW，不 DROP）：
--   ① SET SESSION collation_connection = utf8mb4_unicode_ci（管住字面量默认 collation）
--   ② 表达式显式 COLLATE utf8mb4_unicode_ci（即使将来换会话口径也定型）
--   ③ 定义体与 193 前完全一致（列名/顺序/别名/JOIN 条件不变），只加 collation；
--      DEFINER 保持 `root`@`localhost`（原值），避免换成执行账号后视图权限语义漂移。
--
-- 前置：193 已应用（ops.schema.applied 含 193）；本迁移不改数据、不改任何基表结构。
-- 备份：属表结构类变更，db-migrate.sh 会按风险分级做前置备份（视图无数据，风险等价于结构快照）。
-- 回滚：无自动回滚。原始定义见文末「§原定义快照」，需要回退时去掉 COLLATE 子句、并把
--       SESSION collation_connection 设回 utf8mb4_0900_ai_ci 重新执行本文件即可复现旧态。
-- 执行通道：bash scripts/db-migrate.sh 195_view_collation_rebuild.sql --yes --task dev-20260921-046
-- ============================================================================
USE `jjx_erp_db`;

-- ── ① 改前取证 ────────────────────────────────────────────────────────────
SELECT 'before' AS phase, COLUMN_NAME, COLUMN_TYPE, COLLATION_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'v_inventory_transaction'
   AND COLUMN_NAME = 'transaction_type_name';

-- ── ② 会话口径 + 重建视图 ────────────────────────────────────────────────
SET SESSION character_set_connection = utf8mb4;
SET SESSION collation_connection = 'utf8mb4_unicode_ci';

CREATE OR REPLACE DEFINER = `root`@`localhost` SQL SECURITY DEFINER VIEW `v_inventory_transaction` AS
SELECT
  `t`.`transaction_id`      AS `transaction_id`,
  `t`.`transaction_type`    AS `transaction_type`,
  (CASE `t`.`transaction_type`
     WHEN 'inbound'      THEN '入库'
     WHEN 'outbound'     THEN '出库'
     WHEN 'transfer_in'  THEN '调拨入库'
     WHEN 'transfer_out' THEN '调拨出库'
     WHEN 'adjust'       THEN '盘盈盘亏'
   END) COLLATE utf8mb4_unicode_ci AS `transaction_type_name`,
  `t`.`material_code`       AS `material_code`,
  `t`.`material_name`       AS `material_name`,
  `t`.`batch_no`            AS `batch_no`,
  `t`.`warehouse_id`        AS `warehouse_id`,
  `w`.`warehouse_name`      AS `warehouse_name`,
  `t`.`location_id`         AS `location_id`,
  `l`.`location_name`       AS `location_name`,
  `t`.`quantity`            AS `quantity`,
  `t`.`before_quantity`     AS `before_quantity`,
  `t`.`after_quantity`      AS `after_quantity`,
  `t`.`unit_cost`           AS `unit_cost`,
  `t`.`amount`              AS `amount`,
  `t`.`source_type`         AS `source_type`,
  `t`.`source_no`           AS `source_no`,
  `t`.`transaction_time`    AS `transaction_time`,
  `t`.`operator_name`       AS `operator_name`,
  `t`.`remark`              AS `remark`
FROM ((`jjx_erp_db`.`inventory_transaction` `t`
  LEFT JOIN `jjx_erp_db`.`inventory_warehouse` `w` ON `t`.`warehouse_id` = `w`.`warehouse_id`)
  LEFT JOIN `jjx_erp_db`.`inventory_storage_location` `l` ON `t`.`location_id` = `l`.`location_id`);

-- ── ③ 校验：改后应为 unicode_ci；全库 4 项应为 0/0/0/0 ────────────────────
SELECT 'after' AS phase, COLUMN_NAME, COLUMN_TYPE, COLLATION_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'v_inventory_transaction'
   AND COLUMN_NAME = 'transaction_type_name';

SELECT 'smoke_rows' AS chk, COUNT(*) AS n FROM v_inventory_transaction
UNION ALL
SELECT 'chk_cols_base', COUNT(*) FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
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

-- ── §原定义快照（回滚用；2026-09-22 11:47 取自 information_schema.VIEWS）──
-- DEFINER=root@localhost / SECURITY_TYPE=DEFINER / CHARACTER_SET_CLIENT=utf8mb4 /
-- COLLATION_CONNECTION=utf8mb4_0900_ai_ci（← 残留根因：建视图时的会话 collation）
-- select `t`.`transaction_id` AS `transaction_id`,`t`.`transaction_type` AS `transaction_type`,
-- (case `t`.`transaction_type` when 'inbound' then '入库' when 'outbound' then '出库'
--  when 'transfer_in' then '调拨入库' when 'transfer_out' then '调拨出库' when 'adjust' then '盘盈盘亏' end)
-- AS `transaction_type_name`, ...（其余列与上文一致）
-- from ((`jjx_erp_db`.`inventory_transaction` `t`
--  left join `jjx_erp_db`.`inventory_warehouse` `w` on((`t`.`warehouse_id` = `w`.`warehouse_id`)))
--  left join `jjx_erp_db`.`inventory_storage_location` `l` on((`t`.`location_id` = `l`.`location_id`)))
--
-- ── 执行记录 ─────────────────────────────────────────────────────────────
-- 2026-09-22 生成人 Hermes（任务 2108；生成时未执行）
-- 2026-09-22 11:45 执行：bash scripts/db-migrate.sh 195_view_collation_rebuild.sql --yes --task dev-20260921-046
--   前置备份（schema 快照）：~/jjx-backups/jjx_schema_snapshot_20260922-1145_before-195.sql
--     md5 fbbee6a59d758877775ccb37153955df（已由入口自动登记 backup-index.tsv）
--   结果：transaction_type_name 0900_ai_ci → utf8mb4_unicode_ci；DEFINER 仍 root@localhost；
--         列名/顺序/类型不变；smoke 查询可取（0 行，inventory_transaction 为空）
--   校验：chk_cols_base=0 / chk_tbls=0 / chk_db=0 / chk_cols_view=0
--   版本：ops.schema.applied = …,193,194,195
