-- 任务码：dev-20261029-009
-- 目的：把供应商侧品名 supplier_material_name 并入物料 search_blob。
-- 口径：保留现有字段拼接顺序、尺寸文本逻辑与索引，只在末尾追加供应商品名；不改搜索 SQL。
-- 执行：bash scripts/db-migrate.sh 322_material_search_blob_supplier_name.sql --yes --task dev-20261029-009 --backup <用户手工备份>

ALTER TABLE inventory_material
  MODIFY COLUMN search_blob VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (CONCAT_WS(' ',IFNULL(material_name,''),IFNULL(material_code,''),
      IFNULL(model,''),IFNULL(material_grade,''),IFNULL(color,''),IFNULL(specification,''),
      IFNULL(search_keywords,''),IFNULL(CASE WHEN width_mm IS NULL AND length_mm IS NULL AND thickness_mm IS NULL THEN NULL ELSE CONCAT(IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(width_mm AS CHAR))), ''), IF(length_mm IS NOT NULL OR thickness_mm IS NOT NULL, CONCAT('*', IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(length_mm AS CHAR))), '')), ''), IF(thickness_mm IS NOT NULL, CONCAT('*', IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(thickness_mm AS CHAR))), '')), ''), size_unit) END, ''),
      IFNULL(brand,''),IFNULL(supplier_material_name,''))) STORED;

SELECT COLUMN_NAME, GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'inventory_material'
  AND COLUMN_NAME = 'search_blob';

SELECT material_id, supplier_material_name, search_blob
FROM inventory_material
WHERE supplier_material_name IS NOT NULL
  AND supplier_material_name <> ''
ORDER BY material_id
LIMIT 10;
