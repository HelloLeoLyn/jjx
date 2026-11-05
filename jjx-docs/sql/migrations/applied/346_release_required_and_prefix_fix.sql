-- 任务码：dev-20261105-019（离型膜/离型纸 修正）
-- risk: low（仅 4 条配置行：2 条必填规则 + 2 条品类前缀；不改结构、不动物料）
-- 起因：345 照搬"五条必填"把 brand 设成必填，离型纸没有品牌时无法保存；
--       且 344 的默认前缀灌值时两个子类还不存在，导致 model_prefix 为 NULL，
--       而前缀现已必填（018 口径：为空直接报错）。
-- 幂等：两条 UPDATE 都带条件，重复执行无副作用。
-- 执行：本次为配置行订正，按 CONVENTIONS §2 免备份；已执行并记账。

-- 1）品牌改为非必填（离型膜/离型纸）
UPDATE inventory_material_required_field
   SET required = 0
 WHERE category_code IN ('RAW_AUX_RELEASE_FILM', 'RAW_AUX_RELEASE_PAPER')
   AND field_key = 'brand'
   AND required <> 0;

-- 2）补两个子类的型号前缀（离型膜 RF、离型纸 RP；空值才补）
UPDATE inventory_material_category
   SET model_prefix = 'RF', update_by = 'hermes', update_time = NOW()
 WHERE category_code = 'RAW_AUX_RELEASE_FILM'
   AND (model_prefix IS NULL OR model_prefix = '');

UPDATE inventory_material_category
   SET model_prefix = 'RP', update_by = 'hermes', update_time = NOW()
 WHERE category_code = 'RAW_AUX_RELEASE_PAPER'
   AND (model_prefix IS NULL OR model_prefix = '');
