-- dev-20261009-056：领料明细保存 BOM 来源、替代材料关系和原需求抵扣量。
-- 执行前由用户手工完成全库备份（排除 hr_employee），并通过 scripts/db-migrate.sh 执行。
-- 历史行保持 NULL；新用料计算领料单写入结构化关系，打印时复用现有备注列。
SET @db := DATABASE();
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='source_bom_item_id')=0,
  'ALTER TABLE inventory_outbound_item ADD COLUMN source_bom_item_id BIGINT NULL COMMENT ''对应的BOM明细ID'' AFTER sort_order','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='coverage_quantity')=0,
  'ALTER TABLE inventory_outbound_item ADD COLUMN coverage_quantity DECIMAL(18,4) NULL COMMENT ''本次领料抵扣原BOM需求量'' AFTER source_bom_item_id','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='substitute_of_material_id')=0,
  'ALTER TABLE inventory_outbound_item ADD COLUMN substitute_of_material_id BIGINT NULL COMMENT ''被替代的BOM物料ID'' AFTER coverage_quantity','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='allocation_ratio')=0,
  'ALTER TABLE inventory_outbound_item ADD COLUMN allocation_ratio DECIMAL(18,6) NULL COMMENT ''本次替代换算系数快照'' AFTER substitute_of_material_id','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='allocation_loss_rate')=0,
  'ALTER TABLE inventory_outbound_item ADD COLUMN allocation_loss_rate DECIMAL(8,4) NULL COMMENT ''本次替代损耗率快照'' AFTER allocation_ratio','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='allocation_reason')=0,
  'ALTER TABLE inventory_outbound_item ADD COLUMN allocation_reason VARCHAR(500) NULL COMMENT ''本次替代依据'' AFTER allocation_loss_rate','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item' AND INDEX_NAME='idx_outbound_item_source_bom')=0,
  'ALTER TABLE inventory_outbound_item ADD INDEX idx_outbound_item_source_bom (source_bom_item_id)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=@db AND TABLE_NAME='inventory_outbound_item'
  AND COLUMN_NAME IN ('source_bom_item_id','coverage_quantity','substitute_of_material_id','allocation_ratio','allocation_loss_rate','allocation_reason');
