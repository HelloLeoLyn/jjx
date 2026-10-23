-- dev-20261024-001：物料主数据新模型结构；risk: low
-- 先由用户手工备份（排除hr_employee），仅经db-migrate.sh执行。不启停服务。
-- 原资料独立保存；尺寸不增加同义列；厚度/克重在模板属性中分别以数值保存。

CREATE TABLE IF NOT EXISTS inventory_attribute_template (
  template_id BIGINT NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  template_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  category_id BIGINT NOT NULL COMMENT '末级品类，一个品类一份模板',
  attribute_schema JSON NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用0停用',
  create_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  update_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (template_id),
  UNIQUE KEY uk_attribute_template_code (template_code),
  UNIQUE KEY uk_attribute_template_category (category_id),
  CONSTRAINT fk_attribute_template_category FOREIGN KEY (category_id) REFERENCES inventory_material_category(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料品类属性模板';

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='original_name'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN original_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''来源原名称，新模型必填''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='original_specification'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN original_specification VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''来源原规格，原貌保留''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='material_meta'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN material_meta VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''材质代码，引用material_meta字典''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='material_form'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN material_form VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''形态代码，引用form字典''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='attribute_json'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN attribute_json JSON NULL COMMENT ''按末级品类模板校验的属性值，唯一属性真源''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='is_customer_supplied'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN is_customer_supplied TINYINT NOT NULL DEFAULT 0 COMMENT ''客供专用档案标记，不表达批次所有权''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='is_sample'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN is_sample TINYINT NOT NULL DEFAULT 0 COMMENT ''样品专用档案标记，不表达某次用途''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
