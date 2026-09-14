-- dev-20260912-013：工艺路线实例备注与子工序作业说明分离。
-- description 保留为单工序/子工序作业说明；remark 属于独立工序本身或复合父工序整体。
SET @has_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'engineering_routing_item'
    AND column_name = 'remark'
);
SET @ddl := IF(
  @has_column = 0,
  'ALTER TABLE engineering_routing_item ADD COLUMN remark varchar(500) NULL COMMENT ''工序备注：独立工序自身或复合父工序整体'' AFTER description',
  'SELECT ''engineering_routing_item.remark already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
