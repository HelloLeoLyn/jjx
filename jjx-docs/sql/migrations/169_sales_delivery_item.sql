-- ============================================================================
-- 169_sales_delivery_item.sql
-- 任务码：dev-20260921-039（发货流程级改造：分批发货）
--
-- 背景：发货单表 sales_delivery 只有汇总（total_quantity/total_amount），没有明细，
--   `shipOrder` 只能整单发货：一次把订单全部明细求和写进发货单，订单直接 7生产中→8已发货，
--   出库单按「订单量-已发量」自动算（历史注释 073分批），但**发货单本身不知道发了哪些行、发了多少**
--   → 无法对账到行、无法分批发货、打印的送货单也只能从订单带全量明细。
--
-- 本迁移新增发货明细表：一张发货单 ↔ N 行明细（可只发部分数量）。
--   · 出库单按明细数量出库（见 InventoryOutboundServiceImpl.createFromSalesByDelivery）
--   · 发货单打印/详情按明细展示
--   · 签收/拒收按发货单粒度（含明细，拒收回冲时按明细回库）
--
-- 幂等：CREATE TABLE IF NOT EXISTS；不刷历史数据（现网 sales_delivery 为空表）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sales_delivery_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `delivery_id` bigint NOT NULL COMMENT '发货单ID（sales_delivery.delivery_id）',
  `order_product_id` bigint DEFAULT NULL COMMENT '销售订单明细ID（sales_order_product.id）',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID',
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品编码',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品名称',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '规格描述',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '本次发货数量',
  `unit_price` decimal(18,2) DEFAULT NULL COMMENT '单价',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '金额',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '行备注',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`item_id`),
  KEY `idx_delivery_id` (`delivery_id`),
  KEY `idx_order_product_id` (`order_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售发货明细（分批发货）';
