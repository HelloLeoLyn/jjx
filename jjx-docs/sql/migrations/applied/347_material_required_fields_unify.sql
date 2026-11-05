-- 任务码：dev-20261105-020（品类必填字段可配 + 统一口径草案）
-- risk: low（仅重灌 inventory_material_required_field 配置行；不改结构、不动物料/模板/前缀）
-- 口径：所有品类 材料显示名/颜色/规格 必填；胶带、保护膜、离型膜/纸、电子元件(含子类) 型号必填；
--       品牌仅 胶带、电子元件(含子类) 必填；保护膜/离型/膜片类品牌不强制。
-- 幂等：先按规则 UPDATE 已有行，再 INSERT 缺失行（WHERE NOT EXISTS），重复执行结果不变。
-- 说明：原写法 INSERT ... SELECT ... ON DUPLICATE KEY UPDATE 在 CROSS JOIN 之后会被解析成 JOIN 的 ON，
--       MySQL 直接报 1064，故改为 UPDATE + INSERT 两段；此改动不涉及数据口径变化。
-- 本脚本会重新应用草案口径；上线后不要用它覆盖用户在界面调整的配置。

UPDATE inventory_material_required_field r
JOIN inventory_material_category c ON c.category_code = r.category_code
SET r.required = CASE
      WHEN r.field_key IN ('material_grade', 'color', 'specification') THEN 1
      WHEN r.field_key = 'model' AND (
        c.category_code IN ('RAW_AUX_TAPE', 'RAW_AUX_PROTECT', 'RAW_AUX_RELEASE_FILM', 'RAW_AUX_RELEASE_PAPER')
        OR c.category_code = 'RAW_AUX_ELECTRONIC'
        OR c.category_code LIKE 'RAW!_AUX!_ELECTRONIC!_%' ESCAPE '!'
      ) THEN 1
      WHEN r.field_key = 'brand' AND (
        c.category_code = 'RAW_AUX_TAPE'
        OR c.category_code = 'RAW_AUX_ELECTRONIC'
        OR c.category_code LIKE 'RAW!_AUX!_ELECTRONIC!_%' ESCAPE '!'
      ) THEN 1
      ELSE 0
    END;

INSERT INTO inventory_material_required_field (category_code, field_key, required)
SELECT c.category_code, f.field_key,
       CASE
         WHEN f.field_key IN ('material_grade', 'color', 'specification') THEN 1
         WHEN f.field_key = 'model' AND (
           c.category_code IN ('RAW_AUX_TAPE', 'RAW_AUX_PROTECT', 'RAW_AUX_RELEASE_FILM', 'RAW_AUX_RELEASE_PAPER')
           OR c.category_code = 'RAW_AUX_ELECTRONIC'
           OR c.category_code LIKE 'RAW!_AUX!_ELECTRONIC!_%' ESCAPE '!'
         ) THEN 1
         WHEN f.field_key = 'brand' AND (
           c.category_code = 'RAW_AUX_TAPE'
           OR c.category_code = 'RAW_AUX_ELECTRONIC'
           OR c.category_code LIKE 'RAW!_AUX!_ELECTRONIC!_%' ESCAPE '!'
         ) THEN 1
         ELSE 0
       END
FROM inventory_material_category c
CROSS JOIN (
  SELECT 'material_grade' AS field_key UNION ALL SELECT 'model' UNION ALL
  SELECT 'color' UNION ALL SELECT 'specification' UNION ALL SELECT 'brand'
) f
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_material_required_field x
   WHERE x.category_code = c.category_code AND x.field_key = f.field_key
);
