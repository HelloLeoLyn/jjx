-- 任务码：dev-20261030-010
-- 目的：表面工艺统一成一套「表面处理」多选（12 项），并补齐压克力板/光学扩散/金属箔三个品类。
-- 口径：亮面/哑面/细砂/中砂/粗砂/拉丝/加硬/印刷级/防雾/电镀/网纹/AG防眩；
--       压克力原「加硬方式」并入（当前无物料取值，安全）；保护膜网格暂不动（属胶面纹理，单独字段保留）。
-- 影响面：仅属性模板 fields 定义，不动物料数据。

-- 1) 已有表面处理的四个品类：统一选项集（PC/PET/PVC 在第1列，其他基材在第2列）
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields[0].options', CAST(
  '[{"code":"SMOOTH","label":"亮面"},{"code":"MATTE","label":"哑面"},{"code":"FINE_SAND","label":"细砂"},{"code":"MEDIUM_SAND","label":"中砂"},{"code":"COARSE_SAND","label":"粗砂"},{"code":"BRUSHED","label":"拉丝"},{"code":"HARD_COATED","label":"加硬"},{"code":"PRINT_GRADE","label":"印刷级"},{"code":"ANTI_FOG","label":"防雾"},{"code":"PLATED","label":"电镀"},{"code":"MESH","label":"网纹"},{"code":"AG_ANTI_GLARE","label":"AG防眩"}]' AS JSON))
WHERE template_code IN ('TPL_PC','TPL_PET','TPL_PVC');

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields[1].options', CAST(
  '[{"code":"SMOOTH","label":"亮面"},{"code":"MATTE","label":"哑面"},{"code":"FINE_SAND","label":"细砂"},{"code":"MEDIUM_SAND","label":"中砂"},{"code":"COARSE_SAND","label":"粗砂"},{"code":"BRUSHED","label":"拉丝"},{"code":"HARD_COATED","label":"加硬"},{"code":"PRINT_GRADE","label":"印刷级"},{"code":"ANTI_FOG","label":"防雾"},{"code":"PLATED","label":"电镀"},{"code":"MESH","label":"网纹"},{"code":"AG_ANTI_GLARE","label":"AG防眩"}]' AS JSON))
WHERE template_code = 'TPL_BASE_OTHER';

-- 2) 压克力板：加硬方式并入表面处理（原字段无物料取值）
UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields', CAST(
  '[{"key":"surface_treatment","type":"multienum","label":"表面处理","required":false,"options":[{"code":"SMOOTH","label":"亮面"},{"code":"MATTE","label":"哑面"},{"code":"FINE_SAND","label":"细砂"},{"code":"MEDIUM_SAND","label":"中砂"},{"code":"COARSE_SAND","label":"粗砂"},{"code":"BRUSHED","label":"拉丝"},{"code":"HARD_COATED","label":"加硬"},{"code":"PRINT_GRADE","label":"印刷级"},{"code":"ANTI_FOG","label":"防雾"},{"code":"PLATED","label":"电镀"},{"code":"MESH","label":"网纹"},{"code":"AG_ANTI_GLARE","label":"AG防眩"}]}]' AS JSON))
WHERE template_code = 'TPL_PMMA';

-- 3) 光学/扩散、金属箔/屏蔽：追加表面处理
UPDATE inventory_attribute_template
SET attribute_schema = JSON_ARRAY_APPEND(attribute_schema, '$.fields', CAST(
  '{"key":"surface_treatment","type":"multienum","label":"表面处理","required":false,"options":[{"code":"SMOOTH","label":"亮面"},{"code":"MATTE","label":"哑面"},{"code":"HARD_COATED","label":"加硬"},{"code":"ANTI_FOG","label":"防雾"},{"code":"PLATED","label":"电镀"},{"code":"MESH","label":"网纹"},{"code":"AG_ANTI_GLARE","label":"AG防眩"}]}' AS JSON))
WHERE template_code IN ('TPL_OPTICAL','TPL_FOIL');

SELECT t.template_code, c.category_name,
       SUM(JSON_UNQUOTE(JSON_EXTRACT(jt.item,'$.key')) = 'surface_treatment') AS 有表面处理,
       JSON_LENGTH(JSON_EXTRACT(t.attribute_schema,'$.fields')) AS 字段数
FROM inventory_attribute_template t
LEFT JOIN inventory_material_category c ON c.category_id = t.category_id
JOIN JSON_TABLE(t.attribute_schema, '$.fields[*]' COLUMNS (item JSON PATH '$')) jt ON TRUE
GROUP BY t.template_code, c.category_name, t.attribute_schema
ORDER BY t.template_id;
