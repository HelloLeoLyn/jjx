-- ============================================================================
-- 142_iqc_lot_link.sql
-- 任务码：dev-20260918-026（质量归一·阶段3：IQC 归一，第一步 expand）
--
-- 目的：IQC 归一前置——给"入库明细 + 5 张 IQC 专用单据"加 lot_id 列，
--       用于后续把 IQC 检验事实从 production_quality_inspection 切到 quality_lot。
--
-- 说明：本迁移只【加列】（幂等、可空、不改结构语义），不改任何现有数据/约束。
--       代码侧为 expand 阶段：新模型同步落库，旧路径暂留；读者切换为 026 第二步。
-- 回滚：DROP COLUMN lot_id（各表），见文件末尾注释。
-- ============================================================================

SET @db := DATABASE();

-- inventory_inbound_item.lot_id
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_inbound_item ADD COLUMN lot_id BIGINT NULL COMMENT ''IQC 归一：关联 quality_lot（dev-20260918-026）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_inbound_item' AND column_name='lot_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_quarantine.lot_id
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_iqc_quarantine ADD COLUMN lot_id BIGINT NULL COMMENT ''IQC 归一：关联 quality_lot（dev-20260918-026）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_quarantine' AND column_name='lot_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_rework_order.lot_id
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_iqc_rework_order ADD COLUMN lot_id BIGINT NULL COMMENT ''IQC 归一：关联 quality_lot（dev-20260918-026）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_rework_order' AND column_name='lot_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_return_order.lot_id
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_iqc_return_order ADD COLUMN lot_id BIGINT NULL COMMENT ''IQC 归一：关联 quality_lot（dev-20260918-026）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_return_order' AND column_name='lot_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_scrap_order.lot_id
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_iqc_scrap_order ADD COLUMN lot_id BIGINT NULL COMMENT ''IQC 归一：关联 quality_lot（dev-20260918-026）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_scrap_order' AND column_name='lot_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- inventory_iqc_disposition_order.lot_id
SET @s := (SELECT IF(COUNT(*)=0,
    'ALTER TABLE inventory_iqc_disposition_order ADD COLUMN lot_id BIGINT NULL COMMENT ''IQC 归一：关联 quality_lot（dev-20260918-026）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema=@db AND table_name='inventory_iqc_disposition_order' AND column_name='lot_id');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ============================================================================
-- 执行后自检（应返回 6 行）：
--   SELECT table_name, column_name FROM information_schema.columns
--    WHERE table_schema=DATABASE() AND column_name='lot_id'
--      AND table_name IN ('inventory_inbound_item','inventory_iqc_quarantine',
--        'inventory_iqc_rework_order','inventory_iqc_return_order',
--        'inventory_iqc_scrap_order','inventory_iqc_disposition_order');
--
-- 回滚（单独文件执行）：
--   ALTER TABLE inventory_inbound_item DROP COLUMN lot_id;  （其余 5 张同理）
-- ============================================================================
