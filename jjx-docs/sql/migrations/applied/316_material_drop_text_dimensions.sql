-- dev-20261028-011：尺寸只保留数值真源；search_blob 先解除 size 依赖。
-- 手工全库备份由 db-migrate.sh --backup 校验；不回填/改写既有数值。
ALTER TABLE inventory_material
  MODIFY COLUMN search_blob VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  GENERATED ALWAYS AS (CONCAT_WS(' ',IFNULL(material_name,''),IFNULL(material_code,''),
    IFNULL(model,''),IFNULL(material_grade,''),IFNULL(color,''),IFNULL(specification,''),
    IFNULL(search_keywords,''),IFNULL(CASE WHEN width_mm IS NULL AND length_mm IS NULL AND thickness_mm IS NULL THEN NULL ELSE CONCAT(IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(width_mm AS CHAR))), ''), IF(length_mm IS NOT NULL OR thickness_mm IS NOT NULL, CONCAT('*', IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(length_mm AS CHAR))), '')), ''), IF(thickness_mm IS NOT NULL, CONCAT('*', IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(thickness_mm AS CHAR))), '')), ''), size_unit) END, ''),IFNULL(brand,''))) STORED,
  DROP COLUMN attr_thickness_num,
  DROP COLUMN thickness,
  DROP COLUMN width_size,
  DROP COLUMN size;
