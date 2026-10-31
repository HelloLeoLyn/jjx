-- 任务码：dev-20261031-002
-- 目的：胶带/胶膜（TPL_TAPE / RAW_AUX_TAPE）的「胶型」改为选填，避免没有胶系资料时保存被必填校验拦住。
-- 口径：用户 2026-10-31 选定方案②——取消必填；命名规则保持「缺值跳过」，名称自动省略该段；规格模板不受影响。
-- 影响：单行配置改动（inventory_attribute_template.attribute_schema 里 fields[key=adhesive_type].required），
--       不涉及表结构；该品类当前 0 条物料，对存量名称/规格零影响。可等价于在「物料 → 主数据设置 → 属性模板」取消「必填」勾选。
-- 幂等：仅当该字段当前仍是必填时更新，重复执行影响 0 行。
-- 依据：jjx-docs/standards/CONVENTIONS.md §2（单行配置改动免备份）/ §3（迁移命名与记账）。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(
        attribute_schema,
        REPLACE(JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'adhesive_type', NULL, '$.fields[*].key')), '.key', '.required'),
        FALSE)
WHERE template_code = 'TPL_TAPE'
  AND JSON_SEARCH(attribute_schema, 'one', 'adhesive_type', NULL, '$.fields[*].key') IS NOT NULL
  AND JSON_EXTRACT(attribute_schema,
        REPLACE(JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'adhesive_type', NULL, '$.fields[*].key')), '.key', '.required')) = TRUE;

SELECT template_id, template_code,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].label')) AS 字段,
       JSON_UNQUOTE(JSON_EXTRACT(attribute_schema, '$.fields[*].required')) AS 必填
FROM inventory_attribute_template
WHERE template_code = 'TPL_TAPE';
