-- 56_inventory_stocktake_item_audit_columns.sql
-- 盘点明细表缺列（实体 InventoryStocktakeItem 继承 BaseEntity + 自带盘点人/实盘时间，
-- 表缺列导致 insert/select 报 Unknown column，盘点功能不可用）
-- 与 inventory_stocktake_order 保持一致。任务：第1轮库存链盘点测试发现（2026-09-03）
USE jjx_erp_db;

ALTER TABLE inventory_stocktake_item
    ADD COLUMN stocktake_by   VARCHAR(64)  NULL COMMENT '盘点人' AFTER reason,
    ADD COLUMN stocktake_time DATETIME     NULL COMMENT '实盘时间' AFTER stocktake_by,
    ADD COLUMN create_by      VARCHAR(64)  NULL COMMENT '创建人' AFTER remark,
    ADD COLUMN create_time    DATETIME     NULL COMMENT '创建时间' AFTER create_by,
    ADD COLUMN update_by      VARCHAR(64)  NULL COMMENT '更新人' AFTER create_time,
    ADD COLUMN update_time    DATETIME     NULL COMMENT '更新时间' AFTER update_by;
