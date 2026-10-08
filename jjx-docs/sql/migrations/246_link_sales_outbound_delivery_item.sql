-- dev-20261008-024：合并发货逐行追溯。不新建表，不改历史数量/流水。
-- 应用前由用户手工全库备份（排除 hr_employee），使用 scripts/db-migrate.sh。
-- 字段级评审：delivery_item_id 是出库行到发货原始明细的关系，不能由产品ID代替（同产品跨订单会歧义）。
ALTER TABLE inventory_outbound_item
  ADD COLUMN delivery_item_id BIGINT NULL COMMENT '来源发货明细ID，追溯sales_delivery_item.item_id' AFTER outbound_id,
  ADD INDEX idx_outbound_delivery_item (delivery_item_id);
-- 兼容：历史行留NULL；历史订单型出库继续使用旧路径。新发货型出库必须明确每行来源。
