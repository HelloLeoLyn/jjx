-- 清理 inventory_stock 统一库存改造遗留的历史脏行（inventory_item_id IS NULL）
--
-- 背景：2026-09-09 统一库存身份改造（dev-20260909-010）把 inventory_stock 改为按 inventory_item_id 汇总，
--       唯一键为 uk_inventory_item(inventory_item_id)；但改造前的旧行（按 material_id、inventory_item_id 为空）未清理。
--       MySQL 唯一索引对 NULL 不去重，且旧 refreshSummary 的 ON DUPLICATE KEY 对 NULL 行永不触发，
--       导致同一物料在库存台账出现多行、数量与 inventory_stock_item 不一致，且随业务操作持续新增。
--
-- 影响：库存台账列表重复/数量错；低库存（按 inventory_item_id 关联 inventory_item）漏算；呆滞/预警统计重复。
-- 前置备份：jjx-docs/sql/backups/inventory_stock_legacy_null_cleanup_<时间>.sql（表级 guard）
--
-- 破坏性操作（DELETE）：原因如上，仅删除统一库存改造前遗留的空 inventory_item_id 行。
-- 已核查 information_schema.KEY_COLUMN_USAGE：无任何表以外键引用 inventory_stock，删除不影响引用完整性。
-- 配套代码改动：InventoryStockMapper.refreshSummary 改为按 inventory_item_id 分组写入（不再产生 NULL 行）。

DELETE FROM inventory_stock WHERE inventory_item_id IS NULL;

-- 配套：修正表注释（原文"按物料汇总"，统一库存改造后实为按 inventory_item_id 汇总）
ALTER TABLE inventory_stock COMMENT='库存汇总表（按统一库存物品 inventory_item_id 汇总，动态反映最早批次的库位与有效期）';
