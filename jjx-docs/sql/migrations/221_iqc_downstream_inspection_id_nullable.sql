-- ============================================================================
-- 221_iqc_downstream_inspection_id_nullable.sql
-- 任务码：dev-20260924-023
--
-- 目的：根治「IQC 归一后遗留的过期 NOT NULL 约束」整类问题。
--       220 只修了 inventory_iqc_quarantine 一张表；紧接着处置动作全量报同一错误：
--       Cause: java.sql.SQLException: Field 'inspection_id' doesn't have a default value
--       （Mapper = com.jjx.inventory.mapper.InventoryIqcDispositionOrderMapper.insert）
--       根因与 220 完全相同，只是换了下游单据：
--         ① 这 4 张表的 inspection_id 仍是 bigint NOT NULL 且无默认值；库 sql_mode 含 STRICT_TRANS_TABLES；
--         ② IQC 归一（dev-20260918-026/-027，提交 8d0556f7）把检验事实从 production_quality_inspection
--            切到 quality_lot（该旧表已归档删除：information_schema.tables 里已无任何 %quality_inspection% 表）；
--         ③ 代码从隔离行复制该字段：order.setInspectionId(quarantine.getInspectionId())
--            （InventoryInboundServiceImpl:521 / :605 / :624 / :642），而归一后 quarantine.inspection_id 恒为 NULL
--            → 列缺失 → 严格模式报错。
--       影响面：handleQuarantine 的四个处置动作（RELEASE/RETURN/REWORK/SCRAP）全部失败，
--               其中 disposition_order 是无条件创建的（:515-533），所以连「放行」也过不去。
--       注：这 4 张表没有索引/唯一键涉及 inspection_id（information_schema.statistics 实测），
--           故本迁移只改列可空性，不涉及索引手术。
--
-- 口径（与 220 一致，用户 2026-09-24 拍板 A）：保留该列作历史痕迹，只放开为可空；
--       代码不改（setInspectionId(null) 传空即列省略，不再撞 NOT NULL）。
-- 前置实测（2026-09-24 16:0x，只读）：4 张表均为 0 行，无历史数据风险。
-- 回滚：
--   ALTER TABLE inventory_iqc_disposition_order MODIFY COLUMN inspection_id BIGINT NOT NULL;
--   ALTER TABLE inventory_iqc_return_order      MODIFY COLUMN inspection_id BIGINT NOT NULL;
--   ALTER TABLE inventory_iqc_rework_order      MODIFY COLUMN inspection_id BIGINT NOT NULL;
--   ALTER TABLE inventory_iqc_scrap_order       MODIFY COLUMN inspection_id BIGINT NOT NULL;
--   （回滚前需先清掉 inspection_id 为 NULL 的行，否则会失败）
-- ============================================================================

SET @db := DATABASE();

-- 0) 前置体检：把改前状态打到执行日志（只读）
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_disposition_order'
      AND column_name='inspection_id' AND is_nullable='NO') AS disposition_still_notnull,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_return_order'
      AND column_name='inspection_id' AND is_nullable='NO') AS return_still_notnull,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_rework_order'
      AND column_name='inspection_id' AND is_nullable='NO') AS rework_still_notnull,
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_scrap_order'
      AND column_name='inspection_id' AND is_nullable='NO') AS scrap_still_notnull,
  (SELECT COUNT(*) FROM inventory_iqc_disposition_order) AS rows_disposition,
  (SELECT COUNT(*) FROM inventory_iqc_return_order)      AS rows_return,
  (SELECT COUNT(*) FROM inventory_iqc_rework_order)      AS rows_rework,
  (SELECT COUNT(*) FROM inventory_iqc_scrap_order)       AS rows_scrap;

-- 1) 四张下游单 inspection_id → 可空（幂等：仅当当前仍为 NOT NULL 时才改）
--    information_schema.COLUMNS 一列一行，COUNT(*)=1 即“该列存在且为 NOT NULL”。
SET @s := (SELECT IF(COUNT(*)=1,
    'ALTER TABLE inventory_iqc_disposition_order MODIFY COLUMN inspection_id BIGINT NULL DEFAULT NULL COMMENT ''历史列：IQC 归一(dev-20260918-026)后由 lot_id 承载关联，不再写入''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_disposition_order'
    AND column_name='inspection_id' AND is_nullable='NO');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=1,
    'ALTER TABLE inventory_iqc_return_order MODIFY COLUMN inspection_id BIGINT NULL DEFAULT NULL COMMENT ''历史列：IQC 归一(dev-20260918-026)后由 lot_id 承载关联，不再写入''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_return_order'
    AND column_name='inspection_id' AND is_nullable='NO');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=1,
    'ALTER TABLE inventory_iqc_rework_order MODIFY COLUMN inspection_id BIGINT NULL DEFAULT NULL COMMENT ''历史列：IQC 归一(dev-20260918-026)后由 lot_id 承载关联，不再写入''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_rework_order'
    AND column_name='inspection_id' AND is_nullable='NO');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT IF(COUNT(*)=1,
    'ALTER TABLE inventory_iqc_scrap_order MODIFY COLUMN inspection_id BIGINT NULL DEFAULT NULL COMMENT ''历史列：IQC 归一(dev-20260918-026)后由 lot_id 承载关联，不再写入''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_scrap_order'
    AND column_name='inspection_id' AND is_nullable='NO');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 收尾体检：四列应全部为 YES（可空）
SELECT
  (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_disposition_order' AND column_name='inspection_id') AS disposition_inspection_id,
  (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_return_order' AND column_name='inspection_id') AS return_inspection_id,
  (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_rework_order' AND column_name='inspection_id') AS rework_inspection_id,
  (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema=@db AND table_name='inventory_iqc_scrap_order' AND column_name='inspection_id') AS scrap_inspection_id;
