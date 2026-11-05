-- 任务码：dev-20261105-019（离型材料拆分子类）
-- risk: low
-- 仅新增两个四级品类、各一份属性模板及各五条必填规则，不修改现有品类、模板或物料。
-- 幂等：品类按 category_code、模板按 category_id/template_code、必填规则按
--       category_code + field_key 使用 NOT EXISTS 判重；已有行保留，不覆盖。
-- 执行前须由用户先手工备份，再通过 scripts/db-migrate.sh 执行；本卡仅交付，迁移 345 未执行。
-- 账本：编写时 ops.schema.version=343，ops.schema.applied 最大号=343；
--       迁移 344 已提交但未执行，故本文件取 345。记账由 db-migrate.sh 负责，本文件不写账本。
-- 型号规则及品类型号前缀由用户在界面配置，本迁移不配置、不补写。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 在离型材料（24，三级）下新增离型膜、离型纸；路径沿用父级并追加子类。
INSERT INTO inventory_material_category
    (category_code, category_name, parent_id, category_level, category_path, name_path,
     sort_order, status, create_by, create_time, update_by, update_time)
SELECT v.code, v.name, p.category_id, 4,
       CONCAT(p.category_path, '/', v.code), CONCAT(p.name_path, '/', v.name),
       v.sort_order, '1', 'codex', NOW(), 'codex', NOW()
FROM inventory_material_category p
JOIN (
    SELECT 'RAW_AUX_RELEASE_FILM' AS code, '离型膜' AS name, 1 AS sort_order
    UNION ALL SELECT 'RAW_AUX_RELEASE_PAPER', '离型纸', 2
) v
WHERE p.category_id = 24 AND p.category_code = 'RAW_AUX_RELEASE' AND p.category_level = 3
  AND NOT EXISTS (SELECT 1 FROM inventory_material_category x WHERE x.category_code = v.code);

-- 完整复制父级 schema（含五个属性字段、名称规则及默认单位），仅替换本卡指定配置。
-- 膜：PET / FILM；厚度(mm) * 宽(mm) * 长(mm)，逐段带单位。
INSERT INTO inventory_attribute_template
    (template_code, template_name, category_id, attribute_schema, status,
     create_by, update_by, create_time, update_time)
SELECT 'TPL_RELEASE_FILM', '离型膜属性模板', c.category_id,
       JSON_SET(t.attribute_schema,
           '$.meta_options', JSON_ARRAY('PET'),
           '$.meta_required', CAST('true' AS JSON),
           '$.default_meta', 'PET',
           '$.default_form', 'FILM',
           '$.spec_pattern', CAST('{"fields":[{"key":"thickness_mm","sep":"","unit":"mm"},{"key":"width","sep":"*","unit":"mm"},{"key":"length","sep":"*","unit":"mm"}],"with_unit":true,"show_label":false,"unit_position":"each"}' AS JSON)),
       1, 'codex', 'codex', NOW(), NOW()
FROM inventory_material_category c
JOIN inventory_attribute_template t ON t.category_id = 24
WHERE c.category_code = 'RAW_AUX_RELEASE_FILM' AND c.parent_id = 24 AND c.category_level = 4
  AND NOT EXISTS (SELECT 1 FROM inventory_attribute_template x
                  WHERE x.category_id = c.category_id OR x.template_code = 'TPL_RELEASE_FILM');

-- 纸：PAPER / PAPER；克重(g/m²) * 宽(mm) * 长(mm)，克重单位沿用父级字段定义。
INSERT INTO inventory_attribute_template
    (template_code, template_name, category_id, attribute_schema, status,
     create_by, update_by, create_time, update_time)
SELECT 'TPL_RELEASE_PAPER', '离型纸属性模板', c.category_id,
       JSON_SET(t.attribute_schema,
           '$.meta_options', JSON_ARRAY('PAPER'),
           '$.meta_required', CAST('true' AS JSON),
           '$.default_meta', 'PAPER',
           '$.default_form', 'PAPER',
           '$.spec_pattern', CAST('{"fields":[{"key":"gram_weight_gsm","sep":""},{"key":"width","sep":"*","unit":"mm"},{"key":"length","sep":"*","unit":"mm"}],"with_unit":true,"show_label":false,"unit_position":"each"}' AS JSON)),
       1, 'codex', 'codex', NOW(), NOW()
FROM inventory_material_category c
JOIN inventory_attribute_template t ON t.category_id = 24
WHERE c.category_code = 'RAW_AUX_RELEASE_PAPER' AND c.parent_id = 24 AND c.category_level = 4
  AND NOT EXISTS (SELECT 1 FROM inventory_attribute_template x
                  WHERE x.category_id = c.category_id OR x.template_code = 'TPL_RELEASE_PAPER');

-- 沿用迁移 315 的 category_code/field_key/required 结构；两个新子类各五项必填。
INSERT INTO inventory_material_required_field (category_code, field_key, required)
SELECT c.category_code, f.field_key, 1
FROM inventory_material_category c
JOIN (
    SELECT 'material_grade' AS field_key
    UNION ALL SELECT 'model'
    UNION ALL SELECT 'color'
    UNION ALL SELECT 'specification'
    UNION ALL SELECT 'brand'
) f
WHERE c.category_code IN ('RAW_AUX_RELEASE_FILM', 'RAW_AUX_RELEASE_PAPER')
  AND c.parent_id = 24 AND c.category_level = 4
  AND NOT EXISTS (SELECT 1 FROM inventory_material_required_field x
                  WHERE x.category_code = c.category_code AND x.field_key = f.field_key);
