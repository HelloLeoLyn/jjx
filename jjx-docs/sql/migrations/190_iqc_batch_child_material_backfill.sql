-- ============================================================================
-- ⚠ 撞号改号（2026-09-22，Hermes）：原为 157_，与同号文件 157_inventory_templates_use_bizno_transfer_stocktake_master.sql 撞号；按「后建者改号」改为 190_，内容一字未动。
-- 157_iqc_batch_child_material_backfill.sql
-- 任务码：dev-20260921-020（配套数据回填）
--
-- 目的：REWORK 子批次的 material_id 历史上未写入（代码已修，见 020），把存量 NULL 行
--       按父批次补全（batch 17 = RW-IQW20260921160253192101，父批次 16 → 物料 1601）。
-- 幂等：只写 material_id IS NULL 且父批次有物料的行。
-- ============================================================================

UPDATE inventory_iqc_batch c
  JOIN inventory_iqc_batch p ON p.batch_id = c.parent_batch_id
   SET c.material_id = p.material_id,
       c.update_time = NOW()
 WHERE c.material_id IS NULL
   AND p.material_id IS NOT NULL;
