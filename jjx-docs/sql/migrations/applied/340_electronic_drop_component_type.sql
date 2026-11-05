-- 任务码：dev-20261104-008
-- 目的：子类已表达「元件类型」，删掉重复的 component_type 属性（用户口径 1A/2B）
--   ① 从 TPL_ELECTRONIC* 全部模板里删除 component_type 字段；
--   ② 命名规则同步删除 component_type（分隔符表里的键一并清掉），名称只留 品牌+型号+颜色+客供+样品；
--   ③ 清理存量物料 attribute_json 里的 component_type 残留（不清理会在保存/导入校验时报「该品类不支持属性」）。
-- 幂等：按 JSON_SEARCH 守卫，重复执行影响 0 行。
-- 影响：模板配置 + 物料属性 JSON（3 条 LED 物料），免备份口径见 CONVENTIONS §2（单品类配置 + 3 行属性清理）。
-- 说明：名称重算不在此迁移内——走系统「预览改名差异 → 按规则批量改名」，避免在 SQL 里复制命名逻辑。

-- ① 删除字段
-- 注意：JSON_SEARCH 返回 '$.fields[0].key'，要删的是数组元素，必须换算成 '$[0]'
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields',
        JSON_REMOVE(JSON_EXTRACT(attribute_schema, '$.fields'),
            CONCAT('$[',
                SUBSTRING_INDEX(SUBSTRING_INDEX(
                    JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'component_type', NULL, '$.fields[*].key')), '[', -1), ']', 1),
            ']')))
WHERE template_code LIKE 'TPL_ELECTRONIC%'
  AND JSON_SEARCH(attribute_schema, 'one', 'component_type', NULL, '$.fields[*].key') IS NOT NULL;

-- ② 命名规则去掉 component_type
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.name_pattern.fields',
        JSON_REMOVE(JSON_EXTRACT(attribute_schema, '$.name_pattern.fields'),
            CONCAT('$[',
                SUBSTRING_INDEX(SUBSTRING_INDEX(
                    JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'component_type', NULL, '$.name_pattern.fields')), '[', -1), ']', 1),
            ']')))
WHERE template_code LIKE 'TPL_ELECTRONIC%'
  AND JSON_SEARCH(attribute_schema, 'one', 'component_type', NULL, '$.name_pattern.fields') IS NOT NULL;

UPDATE inventory_attribute_template
SET attribute_schema = JSON_REMOVE(attribute_schema, '$.name_pattern.separators.component_type')
WHERE template_code LIKE 'TPL_ELECTRONIC%'
  AND JSON_EXTRACT(attribute_schema, '$.name_pattern.separators.component_type') IS NOT NULL;

-- ③ 清理存量属性残留
UPDATE inventory_material
SET attribute_json = JSON_REMOVE(attribute_json, '$.component_type'),
    update_by = 'hermes', update_time = NOW()
WHERE category_id IN (29, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41)
  AND JSON_EXTRACT(attribute_json, '$.component_type') IS NOT NULL;

SELECT template_code,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].key')) AS 字段,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.name_pattern')) AS 命名规则
FROM inventory_attribute_template WHERE template_code IN ('TPL_ELECTRONIC', 'TPL_ELECTRONIC_LED', 'TPL_ELECTRONIC_PIN');

SELECT material_id, material_code, material_name, attribute_json FROM inventory_material WHERE material_id IN (8, 9, 10);
