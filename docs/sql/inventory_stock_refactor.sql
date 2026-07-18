-- ============================================================
-- 库存表重构：拆分为汇总表 + 明细表
-- ============================================================

-- 1. 创建库存批次明细表 inventory_stock_item
CREATE TABLE IF NOT EXISTS inventory_stock_item (
    item_id            BIGINT AUTO_INCREMENT COMMENT '明细ID' PRIMARY KEY,
    material_id        BIGINT        NOT NULL COMMENT '物料ID',
    material_code      VARCHAR(50)   NOT NULL COMMENT '物料编码（冗余）',
    material_name      VARCHAR(200)  NOT NULL COMMENT '物料名称（冗余）',
    warehouse_id       BIGINT        NOT NULL COMMENT '仓库ID',
    location_id        BIGINT        NULL COMMENT '库位ID',
    batch_no           VARCHAR(50)   NOT NULL COMMENT '批次号',
    production_date    DATE          NULL COMMENT '生产日期',
    expiry_date        DATE          NULL COMMENT '有效期至',
    quantity           DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '该批次数量',
    reserved_quantity  DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '该批次预留数量',
    unit_cost          DECIMAL(12,4) NULL COMMENT '该批次单位成本',
    status             TINYINT       DEFAULT 1 COMMENT '状态：0=未生效，1=生效',
    last_inbound_time  DATETIME      NULL COMMENT '最后入库时间',
    last_outbound_time DATETIME      NULL COMMENT '最后出库时间',
    create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_material_warehouse_location_batch (material_id, warehouse_id, location_id, batch_no),
    INDEX idx_material_expiry (material_id, expiry_date, status),
    INDEX idx_material_status (material_id, status),
    FOREIGN KEY (material_id) REFERENCES inventory_material (material_id),
    FOREIGN KEY (warehouse_id) REFERENCES inventory_warehouse (warehouse_id),
    FOREIGN KEY (location_id) REFERENCES inventory_storage_location (location_id) ON DELETE SET NULL
) COMMENT '库存批次明细表';

-- 2. 修改 inventory_stock 为汇总表
-- 先备份旧数据（可选）
-- CREATE TABLE inventory_stock_bak_20260521 AS SELECT * FROM inventory_stock;

-- 删除旧表（注意：如果有外键引用需要先处理）
-- DROP TABLE IF EXISTS inventory_stock;

-- 创建新汇总表
CREATE TABLE IF NOT EXISTS inventory_stock (
    stock_id           BIGINT AUTO_INCREMENT COMMENT '汇总记录ID' PRIMARY KEY,
    material_id        BIGINT        NOT NULL COMMENT '物料ID',
    material_code      VARCHAR(50)   NOT NULL COMMENT '物料编码（冗余）',
    material_name      VARCHAR(200)  NOT NULL COMMENT '物料名称（冗余）',
    total_quantity     DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '总库存数量',
    total_reserved     DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '总预留数量',
    available_quantity DECIMAL(12,4) GENERATED ALWAYS AS (total_quantity - total_reserved) STORED COMMENT '可用数量',
    earliest_expiry    DATE          NULL COMMENT '当前最早有效期',
    location_id        BIGINT        NULL COMMENT '最早批次所在库位ID',
    update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_material (material_id),
    FOREIGN KEY (material_id) REFERENCES inventory_material (material_id),
    FOREIGN KEY (location_id) REFERENCES inventory_storage_location (location_id) ON DELETE SET NULL
) COMMENT '库存汇总表（按物料汇总）';

-- 3. 创建刷新汇总的存储过程（可选，用于手动刷新）
-- 刷新指定物料的汇总信息
-- UPDATE inventory_stock s
-- JOIN (
--     SELECT
--         material_id,
--         MIN(expiry_date) AS earliest_expiry,
--         (SELECT location_id FROM inventory_stock_item sub
--          WHERE sub.material_id = i.material_id
--            AND sub.status = 1
--            AND sub.quantity > 0
--          ORDER BY sub.expiry_date ASC, sub.last_inbound_time ASC LIMIT 1) AS earliest_location_id,
--         SUM(quantity) AS total_qty,
--         SUM(reserved_quantity) AS total_res
--     FROM inventory_stock_item i
--     WHERE i.material_id = ? AND i.status = 1
--     GROUP BY i.material_id
-- ) t ON s.material_id = t.material_id
-- SET s.total_quantity = t.total_qty,
--     s.total_reserved = t.total_res,
--     s.earliest_expiry = t.earliest_expiry,
--     s.location_id = t.earliest_location_id;
