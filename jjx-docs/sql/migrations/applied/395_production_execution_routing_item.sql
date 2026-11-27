-- P10：工序执行关联路线明细行（dev-20261125-017）
SET @ddl := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='production_operation_execution'
       AND column_name='routing_item_id') = 0,
  'ALTER TABLE production_operation_execution ADD COLUMN routing_item_id BIGINT NULL COMMENT ''对应工艺路线明细行（P10 用版身份）''',
  'DO 0'));
PREPARE s1 FROM @ddl; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @idx := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='production_operation_execution'
       AND index_name='idx_exec_routing_item') = 0,
  'CREATE INDEX idx_exec_routing_item ON production_operation_execution (routing_item_id)',
  'DO 0'));
PREPARE s2 FROM @idx; EXECUTE s2; DEALLOCATE PREPARE s2;
