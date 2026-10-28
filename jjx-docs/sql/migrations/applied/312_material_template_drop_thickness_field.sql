-- 任务码：dev-20261028-002
-- 目的：厚度/克重从「属性模板字段」移出，统一由尺寸组件与 inventory_material.thickness_mm/grammage 表达。
-- 背景：11 个品类模板的 attribute_schema.fields[0] 都是 thickness_mm，与尺寸入口重复；后端已禁止从属性改厚度
--       （MaterialMasterConfigurationService 驳回 thickness_mm），模板仍列着该字段属于第二入口。
-- 影响面：仅删除模板 JSON 里的 thickness_mm 字段定义；已存 attribute_json 里的 thickness_mm 由保存入口继续剥离。
-- 回滚：从 311 之前的备份恢复 inventory_attribute_template 即可。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_REMOVE(attribute_schema, JSON_UNQUOTE(JSON_SEARCH(attribute_schema, 'one', 'thickness_mm')))
WHERE JSON_SEARCH(attribute_schema, 'one', 'thickness_mm') IS NOT NULL;

SELECT COUNT(*) AS 仍含厚度字段的模板
FROM inventory_attribute_template
WHERE JSON_SEARCH(attribute_schema, 'one', 'thickness_mm') IS NOT NULL;
