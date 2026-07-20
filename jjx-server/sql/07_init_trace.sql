-- 生产追溯日志表
CREATE TABLE IF NOT EXISTS production_trace_log (
  trace_id bigint NOT NULL AUTO_INCREMENT COMMENT '追溯ID',
  trace_type varchar(20) NOT NULL COMMENT '追溯类型: MATERIAL-原料追溯, ORDER-工单追溯, PRODUCT-产品追溯',
  trace_code varchar(100) NOT NULL COMMENT '追溯编码',
  batch_no varchar(100) DEFAULT NULL COMMENT '批次号',
  order_id bigint DEFAULT NULL COMMENT '关联工单ID',
  product_id bigint DEFAULT NULL COMMENT '关联产品ID',
  material_id bigint DEFAULT NULL COMMENT '关联物料ID',
  operation varchar(50) DEFAULT NULL COMMENT '操作: inbound-入库, outbound-出库, start-开工, complete-完工, inspect-质检',
  operator varchar(50) DEFAULT NULL COMMENT '操作人',
  operate_time datetime DEFAULT NULL COMMENT '操作时间',
  detail json DEFAULT NULL COMMENT '操作详情',
  create_by varchar(64) DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (trace_id),
  KEY idx_trace_code (trace_code),
  KEY idx_order_id (order_id),
  KEY idx_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产追溯日志表';
