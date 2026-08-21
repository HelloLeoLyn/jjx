-- ============================================================
-- Migration: V20260809_001__version_control_backfill.sql
-- 打样转正式版本化改造：旧数据迁移
-- 1) 已存在 BOM/Routing 初始化为 V1.0、is_current=1
-- 2) product 表回填 current_bom_version / current_routing_version
-- Applied: 2026-08-09
-- 幂等：可重复执行（只处理空值/缺失值，已有值不覆盖）
-- ============================================================

-- ------------------------------------------------------------
-- 1. engineering_bom：version 空 → V1.0，is_current 空 → 1
--    （历史数据 bom_version='V1' 无 .0，新 version 字段补全为 V1.0）
-- ------------------------------------------------------------
UPDATE engineering_bom
SET version = 'V1.0'
WHERE version IS NULL OR version = '';

UPDATE engineering_bom
SET is_current = 1
WHERE is_current IS NULL;

-- 防御：同一产品存在多个 is_current=1 的 BOM 时，保留最小 bom_id 为当前版本
-- （当前数据无此情况，留作幂等保护）
UPDATE engineering_bom b
JOIN (
    SELECT product_id, MIN(bom_id) AS keep_id
    FROM engineering_bom
    WHERE is_current = 1
    GROUP BY product_id
    HAVING COUNT(*) > 1
) t ON b.product_id = t.product_id AND b.is_current = 1 AND b.bom_id <> t.keep_id
SET b.is_current = 0;

-- ------------------------------------------------------------
-- 2. engineering_routing：同样初始化
-- ------------------------------------------------------------
UPDATE engineering_routing
SET version = 'V1.0'
WHERE version IS NULL OR version = '';

UPDATE engineering_routing
SET is_current = 1
WHERE is_current IS NULL;

-- 防御：同一产品多个 is_current=1 的 Routing 时，保留最小 routing_id
UPDATE engineering_routing r
JOIN (
    SELECT product_id, MIN(routing_id) AS keep_id
    FROM engineering_routing
    WHERE is_current = 1
    GROUP BY product_id
    HAVING COUNT(*) > 1
) t ON r.product_id = t.product_id AND r.is_current = 1 AND r.routing_id <> t.keep_id
SET r.is_current = 0;

-- ------------------------------------------------------------
-- 3. product 回填版本号：直接取该产品当前生效(is_current=1)的 BOM/Routing 的 version
--    （防御逻辑已保证 is_current=1 同产品唯一，直接 JOIN 取值）
--    无生效 BOM/Routing → 置 NULL（清除 ALTER 默认值造成的假 V1.0）
-- ------------------------------------------------------------
UPDATE product p
LEFT JOIN engineering_bom b
  ON b.product_id = p.product_id
 AND b.is_current = 1
 AND b.version IS NOT NULL AND b.version <> ''
SET p.current_bom_version = b.version;

UPDATE product p
LEFT JOIN engineering_routing r
  ON r.product_id = p.product_id
 AND r.is_current = 1
 AND r.version IS NOT NULL AND r.version <> ''
SET p.current_routing_version = r.version;
