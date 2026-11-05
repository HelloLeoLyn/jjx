-- 任务码 dev-20261105-018：品类型号前缀（迁移 344 未执行，仅交付）。
-- risk: high
-- 用户备份后通过 db-migrate.sh 执行；不建唯一索引，不改存量物料型号。
SET @model_prefix_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_material_category' AND COLUMN_NAME = 'model_prefix'
);
SET @model_prefix_ddl = IF(@model_prefix_exists = 0,
    'ALTER TABLE inventory_material_category ADD COLUMN model_prefix VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''型号前缀，保存时必填，2至8位大写字母数字'' AFTER category_code',
    'SELECT 1');
PREPARE model_prefix_stmt FROM @model_prefix_ddl;
EXECUTE model_prefix_stmt;
DEALLOCATE PREPARE model_prefix_stmt;

-- 按任务书草案初始化叶子分类；不覆盖已维护的非空前缀。
-- 当前只有 RAW_AUX_RELEASE（离型材料），沿用 RF；FILM/PAPER 独立编码存在时分别取 RF/RP。
-- 不新建分类；未在草案中的历史节点保持空值，编辑时必须配置前缀。
-- 以子节点存在性判断叶子，兼容三级和四级分类。
UPDATE inventory_material_category c
LEFT JOIN inventory_material_category child ON child.parent_id = c.category_id
JOIN (
    SELECT 'RAW_BASE_PC' code, 'PC' prefix UNION ALL
    SELECT 'RAW_BASE_PET', 'PET' UNION ALL
    SELECT 'RAW_BASE_PVC', 'PVC' UNION ALL
    SELECT 'RAW_BASE_PMMA', 'PMMA' UNION ALL
    SELECT 'RAW_BASE_OTHER', 'OTHB' UNION ALL
    SELECT 'RAW_AUX_PROTECT', 'PROT' UNION ALL
    SELECT 'RAW_AUX_RELEASE', 'RF' UNION ALL
    SELECT 'RAW_AUX_RELEASE_FILM', 'RF' UNION ALL
    SELECT 'RAW_AUX_RELEASE_PAPER', 'RP' UNION ALL
    SELECT 'RAW_AUX_TAPE', 'TAPE' UNION ALL
    SELECT 'RAW_AUX_FOAM', 'FOAM' UNION ALL
    SELECT 'RAW_AUX_FOIL', 'FOIL' UNION ALL
    SELECT 'RAW_AUX_OPTICAL', 'OPT' UNION ALL
    SELECT 'RAW_AUX_OTHER', 'OTHA' UNION ALL
    SELECT 'RAW_INK', 'INK' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_LED', 'LED' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_PIN', 'PIN' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_HOUSING', 'HSG' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_CONNECTOR', 'CONN' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_RESISTOR', 'RES' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_CAPACITOR', 'CAP' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_DIODE', 'DIODE' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_SPRING', 'SPR' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_HARDWARE', 'HW' UNION ALL
    SELECT 'RAW_AUX_ELECTRONIC_OTHER', 'OTHE'
) defaults ON defaults.code = c.category_code
SET c.model_prefix = defaults.prefix
WHERE child.category_id IS NULL AND (c.model_prefix IS NULL OR c.model_prefix = '');


-- 默认规则属于可编辑模板配置，运行时代码不按品类硬编码字段。
-- 已有 meta+厚度规则只将 meta 改为 categoryprefix，保留操作员的顺序、单位和分隔符。
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.model_pattern.fields[0]', 'categoryprefix')
WHERE JSON_EXTRACT(attribute_schema, '$.model_pattern.fields') = JSON_ARRAY('meta', 'thickness_mm');

-- 仅为尚未配置规则的膜/纸模板灌默认值，主动清空 fields 的规则仍保持人工填写。
-- 离型材料历史模板兼容两种基材，未拆分前不强行规定为膜类或纸类。
UPDATE inventory_attribute_template t
JOIN inventory_material_category c ON c.category_id = t.category_id
SET t.attribute_schema = JSON_SET(t.attribute_schema, '$.model_pattern', CAST(
    CASE WHEN c.category_code = 'RAW_AUX_RELEASE_PAPER' THEN
      '{"fields":["categoryprefix","grammage"],"separator":"","empty":"skip"}'
    ELSE
      '{"fields":["categoryprefix","thickness_mm"],"separator":"","empty":"skip","units":{"thickness_mm":"μm"}}'
    END AS JSON))
WHERE c.category_code IN ('RAW_BASE_PC', 'RAW_BASE_PET', 'RAW_BASE_PVC',
    'RAW_AUX_PROTECT', 'RAW_AUX_RELEASE_FILM', 'RAW_AUX_RELEASE_PAPER')
  AND (JSON_EXTRACT(t.attribute_schema, '$.model_pattern') IS NULL
       OR JSON_TYPE(JSON_EXTRACT(t.attribute_schema, '$.model_pattern')) = 'NULL');
