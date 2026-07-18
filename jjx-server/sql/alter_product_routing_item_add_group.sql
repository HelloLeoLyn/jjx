-- 方案二：在 product_routing_item 表中增加组合标识字段
-- 同一组合的工序共享 group_id，NULL 表示独立工序

ALTER TABLE `product_routing_item`
  ADD COLUMN `group_id` bigint DEFAULT NULL COMMENT '组合ID（同一组合的工序共享此ID，NULL表示独立工序）',
  ADD COLUMN `group_order` int DEFAULT NULL COMMENT '组合顺序（第几组）',
  ADD COLUMN `group_name` varchar(200) DEFAULT NULL COMMENT '组合名称',
  ADD KEY `idx_group_id` (`group_id`);
