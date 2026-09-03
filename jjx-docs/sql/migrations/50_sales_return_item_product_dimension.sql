-- 50_sales_return_item_product_dimension.sql
-- 销售退货单产品化改造（1235 设计修正，2026-09-03）
-- 根因：9-1 退货明细被建成物料级（material_*），销售单据应为产品维度；
--      退货收货→自动入库时才做产品→F成品物料解析（口径在库存动作一处）
-- 数据 0 行，直接换字段
ALTER TABLE sales_return_item
  ADD COLUMN product_id BIGINT NULL COMMENT '产品ID' AFTER return_id,
  ADD COLUMN product_code VARCHAR(64) NULL COMMENT '产品编码' AFTER product_id,
  ADD COLUMN product_name VARCHAR(200) NULL COMMENT '产品名称' AFTER product_code,
  ADD COLUMN product_spec VARCHAR(200) NULL COMMENT '产品规格' AFTER product_name,
  DROP COLUMN material_id,
  DROP COLUMN material_code,
  DROP COLUMN material_name,
  DROP COLUMN material_spec;
