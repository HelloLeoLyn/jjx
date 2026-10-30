-- 任务码：dev-20261030-017
-- 目的：把品类属性值并入物料 search_blob，让属性内容（如「元件类型=LED」）能被列表搜索词命中。
-- 口径：在 322 的拼接顺序（名称/编码/型号/材料显示名/颜色/规格/别名/尺寸/品牌/供应商品名）之后追加
--       attribute_json 去 JSON 标点后的文本（键与值都参与检索）；总长按列宽截断到 1000。
-- 幂等：MODIFY 生成列可重复执行；不改任何业务行数据（生成列自动重算）。
-- 执行：bash scripts/db-migrate.sh 332_material_search_blob_attributes.sql --yes --task dev-20261030-017 --backup <用户手工全库备份>

ALTER TABLE inventory_material
  MODIFY COLUMN search_blob VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (LEFT(CONCAT_WS(' ',IFNULL(material_name,''),IFNULL(material_code,''),
      IFNULL(model,''),IFNULL(material_grade,''),IFNULL(color,''),IFNULL(specification,''),
      IFNULL(search_keywords,''),IFNULL(CASE WHEN width_mm IS NULL AND length_mm IS NULL AND thickness_mm IS NULL THEN NULL ELSE CONCAT(IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(width_mm AS CHAR))), ''), IF(length_mm IS NOT NULL OR thickness_mm IS NOT NULL, CONCAT('*', IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(length_mm AS CHAR))), '')), ''), IF(thickness_mm IS NOT NULL, CONCAT('*', IFNULL(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(thickness_mm AS CHAR))), '')), ''), size_unit) END, ''),
      IFNULL(brand,''),IFNULL(supplier_material_name,''),
      IFNULL(NULLIF(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(attribute_json,''),'"',''),'{',''),'}',''),',',' '),':',' ')),''),'')
    ), 1000)) STORED;

SELECT COLUMN_NAME, GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'inventory_material'
  AND COLUMN_NAME = 'search_blob';

-- 回查：属性值必须已进检索列（LED 行预期命中 1 条）
SELECT material_id, LEFT(material_name, 30) AS material_name, search_blob
FROM inventory_material
WHERE search_blob LIKE '%LED%'
ORDER BY material_id;
