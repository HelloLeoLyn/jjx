-- ============================================================================
-- 156_iqc_batch_lineage_hardening.sql
-- 任务码：dev-20260921-020（IQC 批次谱系代码层根治的配套结构变更）
--
-- 目的：
--   ① 新增 source_inbound_id（所属入库单）+ 索引 —— 谱系查询改按入库单过滤，
--      不再依赖 source_inbound_item_id（明细 id 复用会导致历史批次错挂到新单上）。
--   ② 新增 disposed_quantity（已处置量）—— 与 processed_quantity（检验量）分列；
--      原实现两者混用同一列（处置写 add(quantity)、检验写 pass+fail）。
--   ③ 加唯一约束 uk_iqc_batch_material_no(material_id, batch_no) —— 防重复插入
--      （历史重复行已由 155 清理，加约束前已核对无重复）。
--   ④ 回填：source_inbound_id（明细→父批次两轮）、disposed_quantity（处置单汇总；
--      历史 REWORKED 批次按 processed 兜底，因早期处置单已不存在）。
--
-- 幂等：ADD COLUMN/INDEX 前先按 information_schema 判断；回填只写 NULL/0 的行。
-- ============================================================================

-- ① source_inbound_id 列 + 索引
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'inventory_iqc_batch'
                   AND column_name = 'source_inbound_id');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE inventory_iqc_batch ADD COLUMN source_inbound_id bigint DEFAULT NULL AFTER source_inbound_item_id',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'inventory_iqc_batch'
                   AND index_name = 'idx_iqc_batch_inbound');
SET @ddl := IF(@has_idx = 0,
    'ALTER TABLE inventory_iqc_batch ADD INDEX idx_iqc_batch_inbound (source_inbound_id)',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ② disposed_quantity 列
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'inventory_iqc_batch'
                   AND column_name = 'disposed_quantity');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE inventory_iqc_batch ADD COLUMN disposed_quantity decimal(18,4) NOT NULL DEFAULT 0.0000 AFTER processed_quantity',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ 唯一约束（前置已核对无 (material_id,batch_no) 重复）
SET @has_uk := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'inventory_iqc_batch'
                  AND index_name = 'uk_iqc_batch_material_no');
SET @ddl := IF(@has_uk = 0,
    'ALTER TABLE inventory_iqc_batch ADD UNIQUE KEY uk_iqc_batch_material_no (material_id, batch_no)',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ④-1 回填 source_inbound_id：先按明细（要求物料一致，防错挂），再按父批次继承
UPDATE inventory_iqc_batch b
  JOIN inventory_inbound_item i
    ON i.item_id = b.source_inbound_item_id
   AND i.material_id <=> b.material_id
   SET b.source_inbound_id = i.inbound_id
 WHERE b.source_inbound_id IS NULL;

UPDATE inventory_iqc_batch c
  JOIN inventory_iqc_batch p ON p.batch_id = c.parent_batch_id
   SET c.source_inbound_id = p.source_inbound_id
 WHERE c.source_inbound_id IS NULL
   AND p.source_inbound_id IS NOT NULL;

-- ④-2 回填 disposed_quantity：处置单汇总
UPDATE inventory_iqc_batch b
  JOIN (SELECT iqc_batch_id, SUM(quantity) q
          FROM inventory_iqc_disposition_order
         WHERE iqc_batch_id IS NOT NULL
         GROUP BY iqc_batch_id) d ON d.iqc_batch_id = b.batch_id
   SET b.disposed_quantity = d.q
 WHERE b.disposed_quantity = 0;

-- ④-3 历史兜底：早期处置单已不存在，REWORKED 批次的处置量按 processed 记账（当时两义同源）
UPDATE inventory_iqc_batch
   SET disposed_quantity = processed_quantity
 WHERE disposed_quantity = 0
   AND status = 'REWORKED'
   AND processed_quantity > 0;
