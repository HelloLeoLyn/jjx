-- 任务码：dev-20261030-011
-- 目的：保护膜的「网格」并入「表面处理」多选（网纹 MESH），统一表面工艺口径。
-- 依据：网格=胶面压出的网纹，属表面纹理处理；原 grid 为布尔字段，当前无物料取值。
-- 影响面：仅 TPL_PROTECT 的字段定义；粘性（adhesiveness）保留。

UPDATE inventory_attribute_template
SET attribute_schema = JSON_SET(attribute_schema, '$.fields', CAST(
  '[{"key":"adhesiveness","type":"enum","label":"粘性","required":false,"options":[{"code":"LOW","label":"低粘"},{"code":"MEDIUM","label":"中粘"},{"code":"HIGH","label":"高粘"},{"code":"MEDIUM_HIGH","label":"中高粘"}]},
    {"key":"surface_treatment","type":"multienum","label":"表面处理","required":false,"options":[{"code":"SMOOTH","label":"亮面"},{"code":"MATTE","label":"哑面"},{"code":"FINE_SAND","label":"细砂"},{"code":"MEDIUM_SAND","label":"中砂"},{"code":"COARSE_SAND","label":"粗砂"},{"code":"BRUSHED","label":"拉丝"},{"code":"HARD_COATED","label":"加硬"},{"code":"PRINT_GRADE","label":"印刷级"},{"code":"ANTI_FOG","label":"防雾"},{"code":"PLATED","label":"电镀"},{"code":"MESH","label":"网纹"},{"code":"AG_ANTI_GLARE","label":"AG防眩"}]}]' AS JSON))
WHERE template_code = 'TPL_PROTECT';

SELECT t.template_code, c.category_name,
       GROUP_CONCAT(JSON_UNQUOTE(JSON_EXTRACT(jt.item,'$.key')) ORDER BY jt.o SEPARATOR ',') AS 字段
FROM inventory_attribute_template t
LEFT JOIN inventory_material_category c ON c.category_id = t.category_id
JOIN JSON_TABLE(t.attribute_schema, '$.fields[*]' COLUMNS (o FOR ORDINALITY, item JSON PATH '$')) jt ON TRUE
WHERE t.template_code = 'TPL_PROTECT'
GROUP BY t.template_code, c.category_name;
