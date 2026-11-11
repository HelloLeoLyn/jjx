-- dev-20261111-004：制造BOM产出身份、显式下层引用、单位依据与产品主方案 JSON。
-- risk: high（MODIFY NOT NULL→NULL、生成列+唯一索引；执行前须用户手工全库备份）
-- 归属：B 制造BOM后端能力（M1 制造主数据身份）。不建新表；编号按当次账本 355 取 356。

-- ========== 1) product：产品×投入物料主方案 JSON + 乐观锁版本 ==========
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product' AND COLUMN_NAME = 'material_scheme_json');
SET @ddl = IF(@c = 0,
  'ALTER TABLE product ADD COLUMN material_scheme_json json NULL COMMENT ''产品×投入物料主方案映射（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product' AND COLUMN_NAME = 'scheme_revision');
SET @ddl = IF(@c = 0,
  'ALTER TABLE product ADD COLUMN scheme_revision int NOT NULL DEFAULT 0 COMMENT ''主方案JSON乐观锁版本（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 2) engineering_bom：产出身份 + 当前版本唯一兜底 ==========
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom' AND COLUMN_NAME = 'output_type');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom ADD COLUMN output_type varchar(20) NOT NULL DEFAULT ''PRODUCT'' COMMENT ''产出类型 PRODUCT/MATERIAL（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom' AND COLUMN_NAME = 'output_id');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom ADD COLUMN output_id bigint NULL COMMENT ''产出ID：PRODUCT=product_id，MATERIAL=material_id（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE engineering_bom SET output_type = 'PRODUCT' WHERE output_type IS NULL OR output_type = '';
UPDATE engineering_bom SET output_id = product_id WHERE output_id IS NULL AND product_id IS NOT NULL;

-- M1：MATERIAL 方案没有 product_id；先加列/回填，再放开 NOT NULL。
ALTER TABLE engineering_bom MODIFY COLUMN product_id bigint NULL COMMENT '产品ID：仅 PRODUCT 产出必填';

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom' AND COLUMN_NAME = 'current_scope');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom ADD COLUMN current_scope varchar(80) GENERATED ALWAYS AS (IF(is_current = 1, CONCAT(output_type, '':'', output_id), NULL)) STORED COMMENT ''同输出唯一当前版本辅助列（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 执行前只读核对：SELECT output_type, output_id, COUNT(*) FROM engineering_bom WHERE is_current=1 GROUP BY 1,2 HAVING COUNT(*)>1;
SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom' AND INDEX_NAME = 'uk_bom_current_scope');
SET @ddl = IF(@i = 0, 'ALTER TABLE engineering_bom ADD UNIQUE KEY uk_bom_current_scope (current_scope)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom' AND INDEX_NAME = 'idx_bom_output');
SET @ddl = IF(@i = 0, 'ALTER TABLE engineering_bom ADD KEY idx_bom_output (output_type, output_id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 3) engineering_bom_item：显式下层方案 + 单位依据 ==========
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom_item' AND COLUMN_NAME = 'sub_bom_id');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN sub_bom_id bigint NULL COMMENT ''显式下层制造方案BOM：仅make行（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom_item' AND COLUMN_NAME = 'unit_factor');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN unit_factor decimal(18,8) NULL COMMENT ''BOM单位→档案单位换算系数（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom_item' AND COLUMN_NAME = 'unit_factor_basis');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN unit_factor_basis varchar(200) NULL COMMENT ''换算依据说明（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom_item' AND COLUMN_NAME = 'unit_factor_source');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN unit_factor_source varchar(100) NULL COMMENT ''换算依据来源（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_bom_item' AND INDEX_NAME = 'idx_bom_item_sub_bom');
SET @ddl = IF(@i = 0, 'ALTER TABLE engineering_bom_item ADD KEY idx_bom_item_sub_bom (sub_bom_id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 4) engineering_routing：MATERIAL 路线身份 ==========
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_routing' AND COLUMN_NAME = 'output_type');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_routing ADD COLUMN output_type varchar(20) NOT NULL DEFAULT ''PRODUCT'' COMMENT ''产出类型 PRODUCT/MATERIAL（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_routing' AND COLUMN_NAME = 'output_id');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_routing ADD COLUMN output_id bigint NULL COMMENT ''产出ID：PRODUCT=product_id，MATERIAL=material_id（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE engineering_routing SET output_type = 'PRODUCT' WHERE output_type IS NULL OR output_type = '';
UPDATE engineering_routing SET output_id = product_id WHERE output_id IS NULL AND product_id IS NOT NULL;

ALTER TABLE engineering_routing MODIFY COLUMN product_id bigint NULL COMMENT '产品ID：仅 PRODUCT 路线必填';
ALTER TABLE engineering_routing MODIFY COLUMN product_code varchar(50) NULL;
ALTER TABLE engineering_routing MODIFY COLUMN product_name varchar(200) NULL;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_routing' AND COLUMN_NAME = 'route_scope');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_routing ADD COLUMN route_scope varchar(80) GENERATED ALWAYS AS (IF(is_current = 1, CONCAT(output_type, '':'', output_id), NULL)) STORED COMMENT ''同输出唯一当前路线辅助列（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 执行前只读核对：SELECT output_type, output_id, COUNT(*) FROM engineering_routing WHERE is_current=1 GROUP BY 1,2 HAVING COUNT(*)>1;
SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_routing' AND INDEX_NAME = 'uk_routing_current_scope');
SET @ddl = IF(@i = 0, 'ALTER TABLE engineering_routing ADD UNIQUE KEY uk_routing_current_scope (route_scope)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'engineering_routing' AND INDEX_NAME = 'idx_routing_output');
SET @ddl = IF(@i = 0, 'ALTER TABLE engineering_routing ADD KEY idx_routing_output (output_type, output_id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 5) product_work_spec_version：MATERIAL 规范版本身份 ==========
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_work_spec_version' AND COLUMN_NAME = 'output_type');
SET @ddl = IF(@c = 0,
  'ALTER TABLE product_work_spec_version ADD COLUMN output_type varchar(20) NOT NULL DEFAULT ''PRODUCT'' COMMENT ''产出类型 PRODUCT/MATERIAL（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_work_spec_version' AND COLUMN_NAME = 'output_id');
SET @ddl = IF(@c = 0,
  'ALTER TABLE product_work_spec_version ADD COLUMN output_id bigint NULL COMMENT ''产出ID：PRODUCT=product_id，MATERIAL=material_id（dev-20261111-004）''',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE product_work_spec_version SET output_type = 'PRODUCT' WHERE output_type IS NULL OR output_type = '';
UPDATE product_work_spec_version SET output_id = product_id WHERE output_id IS NULL AND product_id IS NOT NULL;
ALTER TABLE product_work_spec_version MODIFY COLUMN product_id bigint NULL COMMENT '产品ID：仅 PRODUCT 规范必填';

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_work_spec_version' AND INDEX_NAME = 'idx_ws_output');
SET @ddl = IF(@i = 0, 'ALTER TABLE product_work_spec_version ADD KEY idx_ws_output (output_type, output_id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SELECT 'bom_output' AS check_item, COUNT(*) AS total, SUM(output_id IS NULL) AS missing_output_id FROM engineering_bom
UNION ALL SELECT 'route_output', COUNT(*), SUM(output_id IS NULL) FROM engineering_routing
UNION ALL SELECT 'work_spec_output', COUNT(*), SUM(output_id IS NULL) FROM product_work_spec_version;
