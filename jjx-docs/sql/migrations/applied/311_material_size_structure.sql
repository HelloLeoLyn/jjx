-- 任务码：dev-20261028-001
-- 目的：物料尺寸结构化——把「尺寸/厚度」从自由文本收敛成唯一数值真源（宽/长/厚/克重 + 单位 + 口径 + 公差）。
-- 背景：现库存 1608 条中 size 纯数字无单位 907 条、带 × 的 288 条、写成料号的 364 条；
--       厚度同时存在于 size 文本、attribute_json.thickness_mm、生成的 attr_thickness_num，属于多真源。
-- 影响面：仅新增列（无删改），历史数据不动；回填由 scripts/material-size-backfill.sql 单独执行。
-- 回滚：DROP 同名列即可（本迁移未改任何既有列）。

ALTER TABLE inventory_material
  ADD COLUMN width_mm DECIMAL(10, 2) NULL COMMENT '宽(mm)：尺寸数值真源之一' AFTER size,
  ADD COLUMN length_mm DECIMAL(12, 2) NULL COMMENT '长(mm)：卷料为展开长度，片材为边长' AFTER width_mm,
  ADD COLUMN thickness_mm DECIMAL(10, 4) NULL COMMENT '厚(mm)：spec_basis=THICKNESS 时必填' AFTER length_mm,
  ADD COLUMN grammage DECIMAL(10, 2) NULL COMMENT '克重 g/m²：spec_basis=GRAMMAGE 时必填' AFTER thickness_mm,
  ADD COLUMN thickness_tolerance DECIMAL(10, 4) NULL COMMENT '厚度公差 ±mm' AFTER grammage,
  ADD COLUMN size_unit VARCHAR(8) NOT NULL DEFAULT 'mm' COMMENT '尺寸单位，显式声明，不做缺省推断' AFTER thickness_tolerance,
  ADD COLUMN spec_basis VARCHAR(12) NOT NULL DEFAULT 'SIZE' COMMENT '规格口径：THICKNESS/GRAMMAGE/SIZE' AFTER size_unit;
