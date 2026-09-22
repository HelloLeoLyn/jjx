-- dev-20260918-027：旧生产质检模型已切换到 quality_lot / quality_lot_item。
-- 执行前提：应用代码不再读写 production_quality_inspection(_item)。
-- 处理策略：先原样归档，校验行数，再删除旧表；重复执行安全。

DELIMITER $$
DROP PROCEDURE IF EXISTS `migrate_182_archive_legacy_quality`$$
CREATE PROCEDURE `migrate_182_archive_legacy_quality`()
BEGIN
  DECLARE v_exists INT DEFAULT 0;
  DECLARE v_source BIGINT DEFAULT 0;
  DECLARE v_archive BIGINT DEFAULT 0;

  SELECT COUNT(*) INTO v_exists
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'production_quality_inspection';
  IF v_exists > 0 THEN
    CREATE TABLE IF NOT EXISTS `archive_production_quality_inspection`
      LIKE `production_quality_inspection`;
    INSERT IGNORE INTO `archive_production_quality_inspection`
      SELECT * FROM `production_quality_inspection`;
    SELECT COUNT(*) INTO v_source FROM `production_quality_inspection`;
    SELECT COUNT(*) INTO v_archive FROM `archive_production_quality_inspection`;
    IF v_archive < v_source THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '182 archive verification failed: inspection row count mismatch';
    END IF;
  END IF;

  SELECT COUNT(*) INTO v_exists
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'production_quality_inspection_item';
  IF v_exists > 0 THEN
    CREATE TABLE IF NOT EXISTS `archive_production_quality_inspection_item`
      LIKE `production_quality_inspection_item`;
    INSERT IGNORE INTO `archive_production_quality_inspection_item`
      SELECT * FROM `production_quality_inspection_item`;
    SELECT COUNT(*) INTO v_source FROM `production_quality_inspection_item`;
    SELECT COUNT(*) INTO v_archive FROM `archive_production_quality_inspection_item`;
    IF v_archive < v_source THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '182 archive verification failed: inspection item row count mismatch';
    END IF;
  END IF;

  SELECT COUNT(*) INTO v_exists
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'production_operation_execution'
    AND constraint_name = 'fk_execution_source_inspection'
    AND constraint_type = 'FOREIGN KEY';
  IF v_exists > 0 THEN
    ALTER TABLE `production_operation_execution`
      DROP FOREIGN KEY `fk_execution_source_inspection`;
  END IF;

  DROP TABLE IF EXISTS `production_quality_inspection_item`;
  DROP TABLE IF EXISTS `production_quality_inspection`;
END$$
CALL `migrate_182_archive_legacy_quality`()$$
DROP PROCEDURE IF EXISTS `migrate_182_archive_legacy_quality`$$
DELIMITER ;
