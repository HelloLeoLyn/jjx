-- 方案A破坏性清理：无历史数据包袱，移除双账本及 F 类产品镜像物料。
-- 执行前备份：jjx_erp_db_backup_20260909-2248_before-unified-inventory-item.sql

UPDATE inventory_stock s
JOIN inventory_material m ON m.material_id=s.material_id AND m.product_id IS NOT NULL
SET s.material_id=NULL;
UPDATE inventory_stock_item s
JOIN inventory_material m ON m.material_id=s.material_id AND m.product_id IS NOT NULL
SET s.material_id=NULL;
UPDATE inventory_inbound_item d
JOIN inventory_material m ON m.material_id=d.material_id AND m.product_id IS NOT NULL
SET d.material_id=NULL;
UPDATE inventory_outbound_item d
JOIN inventory_material m ON m.material_id=d.material_id AND m.product_id IS NOT NULL
SET d.material_id=NULL;
UPDATE inventory_transaction t
JOIN inventory_material m ON m.material_id=t.material_id AND m.product_id IS NOT NULL
SET t.material_id=NULL;

DELETE FROM inventory_material WHERE product_id IS NOT NULL;
DROP TABLE product_stock;
