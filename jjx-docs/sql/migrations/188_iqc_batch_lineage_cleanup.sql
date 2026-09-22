-- ============================================================================
-- ⚠ 撞号改号（2026-09-22，Hermes）：原为 155_，与同号文件 155_engineering_templates_use_bizno.sql 撞号；按「后建者改号」改为 188_，内容一字未动。
-- 155_iqc_batch_lineage_cleanup.sql
-- 任务码：dev-20260921-019（IQC 批次谱系数据修复）
--
-- 背景（2026-09-21 用户核查「不合格品处置 → 批次谱系」）：
--   1) 孤儿批次错挂：batch 1~9 是 09-18 测试数据（对应入库单/明细已被清理）的遗留批次，
--      其 source_inbound_item_id 仍指向旧明细 id(1~7)。今天新建的入库明细又拿到 id 1/2 →
--      「按明细 id 过滤」的谱系查询把 09-18 的 4 条批次挂到了今天的入库单上（物料都对不上）。
--      —— 查询侧缺陷见 dev-20260921-019 的代码部分，本迁移只清数据。
--   2) 重复/错类批次：
--      · batch 11 与 12 同为 RW-IQW20260921114334147270，11 的 material_id 为 NULL
--        （建子批次时未写 material_id → 按 material+batch_no 的幂等判定失效，插了两遍）；
--      · batch 15 用子批次号 RW-IQW20260921115353448512 冒充 ORIGINAL 原始批次
--        （quantity 100 / SOURCE / root 指向自己），与真子批次 14 冲突。
--   3) 子批次 material_id 缺失：14 为 NULL。
--
-- 事实核对（迁移前，2026-09-21 16:11 实测）：
--   · 9 张关联表（inbound_item / quarantine / disposition / rework.iqc_batch / rework.child_batch /
--     return / scrap / stock_item / transaction）对 batch 1~9、11、15 的引用数**全部为 0**；
--   · 仅有的 FK 是 parent_batch_id 自引用，这些行也没有子批次。
--   → 删除安全（已做 guard 备份：~/jjx-backups/inventory_iqc_batch_20260921-1611_*.sql，
--     md5 2f74b5fdfb8d1e0508986a8985328b03）。
--
-- 未改动（复核后确认属正常口径，勿误改）：
--   · batch 13（原批 100，processed 8 / remaining 92、accepted 0）：8 件经返工进子批次 14，
--     合格数记在子批次上 —— 父批 accepted=0 是正确口径；
--   · 子批次的 processed 与 accepted 相等（如 12/14）：processed 表示"本批已检验/已处理量"，两者相等正常。
--   ⚠️ 遗留口径问题（属代码层，见 dev-20260921-019）：processed_quantity 同时被
--     「处置量」(updateBatchAfterDisposition) 与「检验量」(updateIqcBatchResult) 写入，含义混用。
--
-- 幂等：按显式 id + 存在性守卫，可重复执行。
-- ============================================================================

-- A) 删除 09-18 遗留孤儿批次 1~9（无入库明细、无任何引用、无子批次）
--    ⚠️ MySQL 不允许 DELETE 的子查询里再读同一张表（ERROR 1093），故不写 NOT EXISTS 守卫；
--    安全性由两点保障：① 迁移前实测九张关联表引用数均为 0；② parent_batch_id 自引用 FK 会拒绝删除仍有子批次的行。
DELETE FROM inventory_iqc_batch WHERE batch_id BETWEEN 1 AND 9;

-- B) 删除重复/错类批次：11（material_id 为 NULL 的重复子批次）、15（冒充 ORIGINAL 的幽灵批次）
DELETE FROM inventory_iqc_batch WHERE batch_id IN (11, 15);

-- C) 子批次补 material_id（14 → 1567，取自其父批次 13）
UPDATE inventory_iqc_batch b
   JOIN inventory_iqc_batch p ON p.batch_id = b.parent_batch_id
   SET b.material_id = p.material_id,
       b.update_time = NOW()
 WHERE b.batch_id = 14
   AND b.material_id IS NULL;
