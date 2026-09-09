-- 方案A：库存引擎从 material_id 切换为 inventory_item_id。
-- 材料与产品的业务档案仍分立，material_id 仅作材料来源兼容字段。

ALTER TABLE inventory_stock DROP FOREIGN KEY inventory_stock_ibfk_1;
ALTER TABLE inventory_stock DROP INDEX uk_material;
ALTER TABLE inventory_stock MODIFY material_id BIGINT NULL COMMENT '材料来源ID（产品库存为NULL）';
ALTER TABLE inventory_stock ADD UNIQUE KEY uk_inventory_item (inventory_item_id);
ALTER TABLE inventory_stock ADD CONSTRAINT fk_stock_inventory_item
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (inventory_item_id);

ALTER TABLE inventory_stock_item DROP FOREIGN KEY inventory_stock_item_ibfk_1;
ALTER TABLE inventory_stock_item DROP INDEX uk_material_warehouse_location_batch;
ALTER TABLE inventory_stock_item MODIFY material_id BIGINT NULL COMMENT '材料来源ID（产品库存为NULL）';
ALTER TABLE inventory_stock_item ADD UNIQUE KEY uk_item_warehouse_location_batch
    (inventory_item_id, warehouse_id, location_id, batch_no);
ALTER TABLE inventory_stock_item ADD KEY idx_inventory_item_expiry (inventory_item_id, expiry_date, status);
ALTER TABLE inventory_stock_item ADD CONSTRAINT fk_stock_item_inventory_item
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (inventory_item_id);

ALTER TABLE inventory_inbound_item DROP FOREIGN KEY fk_inbound_item_material;
ALTER TABLE inventory_inbound_item MODIFY material_id BIGINT NULL COMMENT '材料来源ID（产品入库为NULL）';
ALTER TABLE inventory_inbound_item ADD KEY idx_inbound_inventory_item (inventory_item_id);
ALTER TABLE inventory_inbound_item ADD CONSTRAINT fk_inbound_item_inventory_item
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (inventory_item_id);

ALTER TABLE inventory_outbound_item DROP FOREIGN KEY fk_outbound_item_material;
ALTER TABLE inventory_outbound_item MODIFY material_id BIGINT NULL COMMENT '材料来源ID（产品出库为NULL）';
ALTER TABLE inventory_outbound_item ADD KEY idx_outbound_inventory_item (inventory_item_id);
ALTER TABLE inventory_outbound_item ADD CONSTRAINT fk_outbound_item_inventory_item
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (inventory_item_id);

ALTER TABLE inventory_transaction MODIFY material_id BIGINT NULL COMMENT '材料来源ID（产品流水为NULL）';
ALTER TABLE inventory_transaction ADD KEY idx_transaction_inventory_item (inventory_item_id, transaction_time);
ALTER TABLE inventory_transaction ADD CONSTRAINT fk_transaction_inventory_item
    FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (inventory_item_id);

ALTER TABLE inventory_stocktake_item ADD KEY idx_stocktake_inventory_item (inventory_item_id);
ALTER TABLE inventory_transfer_item ADD KEY idx_transfer_inventory_item (inventory_item_id);
ALTER TABLE sales_order_stock_reserve ADD KEY idx_reserve_inventory_item (inventory_item_id);
