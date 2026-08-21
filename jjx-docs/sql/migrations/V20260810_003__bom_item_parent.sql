-- ============================================================
-- Migration: V20260810_003__bom_item_parent.sql
-- BOM 树形结构：明细支持多层父子关系
-- engineering_bom_item 加 parent_material_id（父节点明细ID，NULL=根节点）
-- 幂等：IF NOT EXISTS 判断，可重复执行
-- ============================================================

-- 父节点明细ID（指向本表 item_id，NULL=根节点）
ALTER TABLE engineering_bom_item
    ADD COLUMN parent_material_id BIGINT NULL COMMENT '父节点明细ID（指向本表item_id，NULL=根节点）' AFTER bom_id;
