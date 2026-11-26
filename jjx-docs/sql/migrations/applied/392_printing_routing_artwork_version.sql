-- P08：印刷工序绑定图案版本（dev-20261125-014）
-- 幂等：列/索引已存在则跳过
SET @ddl := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'engineering_routing_item'
      AND column_name = 'artwork_version_id') = 0,
  'ALTER TABLE engineering_routing_item ADD COLUMN artwork_version_id BIGINT NULL COMMENT ''印刷工序绑定的图案版本ID''',
  'DO 0'));
PREPARE s1 FROM @ddl; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @idx := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'engineering_routing_item'
      AND index_name = 'idx_routing_item_artwork_version') = 0,
  'CREATE INDEX idx_routing_item_artwork_version ON engineering_routing_item (artwork_version_id)',
  'DO 0'));
PREPARE s2 FROM @idx; EXECUTE s2; DEALLOCATE PREPARE s2;
