-- 在 product_routing_item 表中增加前置工序字段
-- 只支持 FS（完成-开始）依赖类型，多个前置的逻辑固定为 AND

ALTER TABLE `product_routing_item`
  ADD COLUMN `predecessor_ids` varchar(500) DEFAULT NULL COMMENT '前置工序ID列表，逗号分隔（无空格），例如 "101,102"；NULL 或空字符串表示无前置';
