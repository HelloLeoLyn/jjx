-- 任务码：dev-20261028-015
-- 目的：补「供应商侧品名/叫法」，采购下单与来料核对用；内部单据仍用 material_name。
-- 口径：一个物料一个供应商（与现有 supplier_id/supplier_name 一致），不做多供应商子表。
-- 影响面：仅新增可空列，历史数据不回填。

ALTER TABLE inventory_material
  ADD COLUMN supplier_material_name VARCHAR(200) NULL
  COMMENT '供应商侧品名/叫法（采购单与来料核对用）' AFTER supplier_name;
