-- 359 production_order 产出身份（dev-20261111-008，F阶段 半成品生产库存闭环）
-- 目的：工单产出可能是产品(PRODUCT)或材料/半成品(MATERIAL)，与 engineering_bom.output_type/output_id 同构。
-- 风险：medium（加列 + 一次性回填）；回退：停用入口并保留列，确认无引用后再删列。
-- 前置：当天手工全库备份；执行 scripts/db-migrate.sh 359_... --yes --task dev-20261111-008 --backup <备份.sql>
-- 不做：不改 product 域、不建表、不重算历史库存。

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='production_order' AND COLUMN_NAME='output_type');
SET @ddl = IF(@c = 0,
  'ALTER TABLE production_order ADD COLUMN output_type varchar(20) NULL COMMENT ''产出身份：PRODUCT/MATERIAL'' AFTER product_id',
  'SELECT 1');
PREPARE s1 FROM @ddl; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='production_order' AND COLUMN_NAME='output_id');
SET @ddl = IF(@c = 0,
  'ALTER TABLE production_order ADD COLUMN output_id bigint NULL COMMENT ''产出对象ID：PRODUCT→product_id；MATERIAL→inventory_material.material_id'' AFTER output_type',
  'SELECT 1');
PREPARE s2 FROM @ddl; EXECUTE s2; DEALLOCATE PREPARE s2;

-- 历史工单全部按“产品产出”回填，保证既有行为不变
UPDATE production_order
   SET output_type = 'PRODUCT', output_id = product_id
 WHERE output_type IS NULL AND product_id IS NOT NULL;

SET @c = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='production_order' AND INDEX_NAME='idx_prod_order_output');
SET @ddl = IF(@c = 0,
  'ALTER TABLE production_order ADD INDEX idx_prod_order_output (output_type, output_id)',
  'SELECT 1');
PREPARE s3 FROM @ddl; EXECUTE s3; DEALLOCATE PREPARE s3;
