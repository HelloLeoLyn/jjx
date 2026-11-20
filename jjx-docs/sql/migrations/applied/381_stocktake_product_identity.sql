-- risk: low
-- dev-20261120-004：成品盘点使用inventory_item PRODUCT身份，材料ID沿原库存为空，不填伪材料ID。
-- 仅放宽既有列可空，不改历史数据，不新增列或表；已有材料盘点保持原值。
ALTER TABLE inventory_stocktake_item MODIFY COLUMN material_id BIGINT NULL COMMENT '材料档案ID；成品为空，统一身份见inventory_item_id';
