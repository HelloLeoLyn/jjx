-- dev-20260930-019
-- risk: low
-- 齐套预警 P2：订单成品「占用」落数据——给 sales_order_stock_reserve 增加「占用类型」。
--   reserve_type: 1=实预留（占现货批次，既有行语义） 2=待生产（缺货段，占未来产出）
-- 背景：此前"货到才算占用"，缺货段不进占用登记 → 别的订单把该产品现货当无主（002 案例）。
-- 措施：审核时除现货实预留外，把缺货段也登记为 reserve_type=2 的占用行。
-- 影响：仅新增一列（默认 1，存量行自动成为"实预留"）；不更新、不删除任何业务行。
-- 幂等：列已存在则跳过。

SET @has_col = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'sales_order_stock_reserve'
     AND column_name = 'reserve_type'
);
SET @add_col = IF(
  @has_col = 0,
  'ALTER TABLE sales_order_stock_reserve ADD COLUMN reserve_type TINYINT NOT NULL DEFAULT 1 COMMENT ''占用类型 1=实预留(占批次) 2=待生产(缺货段)'' AFTER status',
  'SELECT 1'
);
PREPARE add_reserve_type FROM @add_col;
EXECUTE add_reserve_type;
DEALLOCATE PREPARE add_reserve_type;

-- 收尾自检（应返回 1）
SELECT COUNT(*) AS has_reserve_type FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'sales_order_stock_reserve'
   AND column_name = 'reserve_type';
