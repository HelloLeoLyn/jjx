-- dev-20261024-007 阶段B（仅准备，正式库待用户下一次手工备份后另行授权应用）。
-- risk: high；DROP COLUMN不可事务回滚。
-- 回滚：从应用前的用户备份恢复这六列的定义、索引与逐行原值（按material_id），
-- 同时恢复search_blob原表达式，并配套回退代码；仅ADD空列不等于恢复。
-- 前置：305/306已应用，ops.schema.version=306；008分类/属性回填完成；
-- 当前分类非空率1041/1607（566未归类保持NULL），主表54列；跨模块适配已就绪。
-- original_name / original_specification 保留；等008回填人工确认后另行授权删除。
-- 历史编码、分类、属性、价格、库存与业务单据均不改写。
-- 正式应用唯一入口：db-migrate.sh + 用户新手工备份；本轮不得调用正式写模式。

-- 应用前人工逐项执行以下只读SQL，核对目标DATABASE()正确。
SELECT DATABASE() AS target_schema;
SELECT config_value AS schema_version FROM sys_config WHERE config_key='ops.schema.version';
SELECT COUNT(*) AS total, SUM(category_id IS NOT NULL) AS categorized,
       SUM(category_id IS NULL) AS unclassified FROM inventory_material;
SELECT COUNT(*) AS material_columns FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material';

-- 视图依赖：候选结果必须为空；同时检查跨库视图（定义无权读取时须由DBA补核）。
SELECT TABLE_SCHEMA,TABLE_NAME,VIEW_DEFINITION FROM information_schema.VIEWS
 WHERE VIEW_DEFINITION LIKE '%inventory_material%'
   AND REGEXP_LIKE(VIEW_DEFINITION,'(^|[^a-zA-Z0-9_])(material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)([^a-zA-Z0-9_]|$)');
-- SELECT *视图在MySQL中存储为展开列；上述查询同时覆盖展开列。

-- FK依赖：包含本表出向和所有表指向本表的外键，必须为空。
SELECT CONSTRAINT_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,
       REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME
 FROM information_schema.KEY_COLUMN_USAGE
 WHERE (TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'
        AND REFERENCED_TABLE_NAME IS NOT NULL AND COLUMN_NAME IN
          ('material_type','material_category','material_name_en','spec_supplement','cost_price_from','cost_price_time'))
    OR (REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='inventory_material'
        AND REFERENCED_COLUMN_NAME IN
          ('material_type','material_category','material_name_en','spec_supplement','cost_price_from','cost_price_time'));

-- 生成列依赖：当前唯一预期结果search_blob引用material_name_en，由下方先解除。
-- 任何其它结果都必须先处理，不得直接应用；解除后重跑应为0行。
SELECT COLUMN_NAME,GENERATION_EXPRESSION FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'
   AND REGEXP_LIKE(GENERATION_EXPRESSION,'(^|[^a-zA-Z0-9_])(material_type|material_category|material_name_en|spec_supplement|cost_price_from|cost_price_time)([^a-zA-Z0-9_]|$)');

-- 破坏性DDL：重建检索生成列表达式，消除英文名依赖；保留原索引。
ALTER TABLE inventory_material
  MODIFY COLUMN search_blob VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (CONCAT_WS(' ',IFNULL(material_name,''),IFNULL(material_code,''),
      IFNULL(model,''),IFNULL(material_grade,''),IFNULL(color,''),IFNULL(specification,''),
      IFNULL(search_keywords,''),IFNULL(size,''),IFNULL(brand,''))) STORED;

-- 再次执行上方生成列依赖查询应为空；MySQL也会拒绝仍有依赖的DROP。
ALTER TABLE inventory_material
  DROP COLUMN material_type,
  DROP COLUMN material_category,
  DROP COLUMN material_name_en,
  DROP COLUMN spec_supplement,
  DROP COLUMN cost_price_from,
  DROP COLUMN cost_price_time;

-- 应用后只读核验：48列，保留两列原始值与两列价格；由执行入口负责版本记账。
SELECT COUNT(*) AS material_columns FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material';
