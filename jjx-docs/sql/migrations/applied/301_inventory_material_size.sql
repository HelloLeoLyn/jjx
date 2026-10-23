-- dev-20261023-012：物料主数据单一尺寸字段；手工全库备份由用户提供。
-- risk: low
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='size'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN size VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''尺寸（缺省mm，可写300/300×305/300×1.0/0.3m×1.0）'' AFTER specification');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 保留009的search_blob分词LIKE入口，新增尺寸/品牌也必须可检索。
ALTER TABLE inventory_material MODIFY COLUMN search_blob VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
GENERATED ALWAYS AS (CONCAT_WS(' ', IFNULL(material_name,''), IFNULL(material_name_en,''), IFNULL(material_code,''), IFNULL(model,''), IFNULL(material_grade,''), IFNULL(color,''), IFNULL(specification,''), IFNULL(search_keywords,''), IFNULL(size,''), IFNULL(brand,''))) STORED;
