-- dev-20260909-006~011：方案A，材料/产品档案分开，库存统一为 inventory_item。
-- 本脚本为可前滚基础迁移；破坏性清理 product_stock/F类产品镜像见后续独立迁移。

CREATE TABLE IF NOT EXISTS inventory_item (
    inventory_item_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '统一库存物品ID',
    item_type VARCHAR(20) NOT NULL COMMENT '来源类型：MATERIAL/PRODUCT',
    source_id BIGINT NOT NULL COMMENT '来源档案ID：material_id/product_id',
    item_code VARCHAR(50) NOT NULL COMMENT '库存物品编码',
    item_name VARCHAR(200) NOT NULL COMMENT '库存物品名称',
    specification VARCHAR(500) NULL COMMENT '规格型号',
    unit VARCHAR(20) NULL DEFAULT 'PCS' COMMENT '库存单位',
    batch_managed TINYINT NOT NULL DEFAULT 1 COMMENT '是否批次管理',
    location_managed TINYINT NOT NULL DEFAULT 1 COMMENT '是否库位管理',
    safe_stock DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '安全库存',
    max_stock DECIMAL(12,4) NULL COMMENT '最高库存',
    reorder_point DECIMAL(12,4) NULL COMMENT '再订货点',
    default_warehouse_id BIGINT NULL COMMENT '默认仓库',
    default_location_id BIGINT NULL COMMENT '默认库位',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (inventory_item_id),
    UNIQUE KEY uk_inventory_item_source (item_type, source_id),
    UNIQUE KEY uk_inventory_item_code (item_code),
    KEY idx_inventory_item_type_status (item_type, status),
    CONSTRAINT fk_inventory_item_warehouse FOREIGN KEY (default_warehouse_id)
        REFERENCES inventory_warehouse (warehouse_id) ON DELETE SET NULL,
    CONSTRAINT fk_inventory_item_location FOREIGN KEY (default_location_id)
        REFERENCES inventory_storage_location (location_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一库存物品主数据';

-- 有 product_id 的 F 类记录是旧的“产品镜像物料”，不再作为 MATERIAL 库存物品导入。
INSERT INTO inventory_item (
    item_type, source_id, item_code, item_name, specification, unit,
    batch_managed, location_managed, safe_stock, max_stock, reorder_point,
    default_warehouse_id, default_location_id, status
)
SELECT 'MATERIAL', m.material_id, m.material_code, m.material_name, m.specification, m.unit,
       COALESCE(m.batch_control, 0), 1, COALESCE(m.safe_stock, 0), m.max_stock, m.reorder_point,
       m.default_warehouse_id, m.default_location_id, m.status
FROM inventory_material m
WHERE m.product_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM inventory_item i
      WHERE i.item_type = 'MATERIAL' AND i.source_id = m.material_id
  );

INSERT INTO inventory_item (
    item_type, source_id, item_code, item_name, specification, unit,
    batch_managed, location_managed, safe_stock, max_stock, reorder_point, status
)
SELECT 'PRODUCT', p.product_id, p.product_code, p.product_name, NULL, p.unit,
       1, 1, COALESCE(ps.safe_stock, 0), ps.max_stock, ps.reorder_point, 1
FROM product p
LEFT JOIN product_stock ps ON ps.product_id = p.product_id
WHERE NOT EXISTS (
    SELECT 1 FROM inventory_item i
    WHERE i.item_type = 'PRODUCT' AND i.source_id = p.product_id
);

ALTER TABLE inventory_stock ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER stock_id;
ALTER TABLE inventory_stock_item ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER item_id;
ALTER TABLE inventory_inbound_item ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER inbound_id;
ALTER TABLE inventory_outbound_item ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER outbound_id;
ALTER TABLE inventory_transaction ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER transaction_id;
ALTER TABLE inventory_stocktake_item ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER stocktake_id;
ALTER TABLE inventory_transfer_item ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER transfer_id;
ALTER TABLE sales_order_stock_reserve ADD COLUMN inventory_item_id BIGINT NULL COMMENT '统一库存物品ID' AFTER order_no;

-- 旧 F 类产品镜像物料的历史库存直接归并到 PRODUCT 身份。
UPDATE inventory_stock s
JOIN inventory_material m ON m.material_id=s.material_id AND m.product_id IS NOT NULL
JOIN inventory_item i ON i.item_type='PRODUCT' AND i.source_id=m.product_id
SET s.inventory_item_id=i.inventory_item_id WHERE s.inventory_item_id IS NULL;
UPDATE inventory_stock_item s
JOIN inventory_material m ON m.material_id=s.material_id AND m.product_id IS NOT NULL
JOIN inventory_item i ON i.item_type='PRODUCT' AND i.source_id=m.product_id
SET s.inventory_item_id=i.inventory_item_id WHERE s.inventory_item_id IS NULL;
UPDATE inventory_inbound_item d
JOIN inventory_material m ON m.material_id=d.material_id AND m.product_id IS NOT NULL
JOIN inventory_item i ON i.item_type='PRODUCT' AND i.source_id=m.product_id
SET d.inventory_item_id=i.inventory_item_id WHERE d.inventory_item_id IS NULL;
UPDATE inventory_outbound_item d
JOIN inventory_material m ON m.material_id=d.material_id AND m.product_id IS NOT NULL
JOIN inventory_item i ON i.item_type='PRODUCT' AND i.source_id=m.product_id
SET d.inventory_item_id=i.inventory_item_id WHERE d.inventory_item_id IS NULL;
UPDATE inventory_transaction t
JOIN inventory_material m ON m.material_id=t.material_id AND m.product_id IS NOT NULL
JOIN inventory_item i ON i.item_type='PRODUCT' AND i.source_id=m.product_id
SET t.inventory_item_id=i.inventory_item_id WHERE t.inventory_item_id IS NULL;

UPDATE inventory_stock s JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=s.material_id
SET s.inventory_item_id=i.inventory_item_id WHERE s.inventory_item_id IS NULL;
UPDATE inventory_stock_item s JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=s.material_id
SET s.inventory_item_id=i.inventory_item_id WHERE s.inventory_item_id IS NULL;
UPDATE inventory_inbound_item d JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=d.material_id
SET d.inventory_item_id=i.inventory_item_id WHERE d.inventory_item_id IS NULL;
UPDATE inventory_outbound_item d JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=d.material_id
SET d.inventory_item_id=i.inventory_item_id WHERE d.inventory_item_id IS NULL;
UPDATE inventory_transaction t JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=t.material_id
SET t.inventory_item_id=i.inventory_item_id WHERE t.inventory_item_id IS NULL;
UPDATE inventory_stocktake_item d JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=d.material_id
SET d.inventory_item_id=i.inventory_item_id WHERE d.inventory_item_id IS NULL;
UPDATE inventory_transfer_item d JOIN inventory_item i ON i.item_type='MATERIAL' AND i.source_id=d.material_id
SET d.inventory_item_id=i.inventory_item_id WHERE d.inventory_item_id IS NULL;
UPDATE sales_order_stock_reserve r JOIN inventory_item i ON i.item_type='PRODUCT' AND i.source_id=r.product_id
SET r.inventory_item_id=i.inventory_item_id WHERE r.inventory_item_id IS NULL;
