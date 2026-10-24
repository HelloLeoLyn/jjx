-- dev-20261024-015：物料主数据迁移期收尾；用户明确直接删除、不迁移原文数据。
-- risk: high；DROP COLUMN/TABLE不能事务回滚。仅本文件通过db-migrate执行，不启停服务。
-- 前置：账本308；代码已清除三列与302双表的运行时读写；原文归档Excel已存在。
-- 原文唯一留档：/home/administrator/物料原名称原规格留档_20261024.xlsx。
-- 用户回退点：jjx-docs/sql/backups/jjx_erp_db_backup_20261024-1743.sql（不生成新备份）。
-- 回滚需用户另行授权：先回退配套代码，再从上述备份恢复两表CREATE/INSERT及三列逐行原值。
-- 列定义恢复语句（仅补结构不等于数据恢复）：
-- ALTER TABLE inventory_material_category ADD COLUMN material_type CHAR(1) NULL COMMENT '所属大类：R/S/F/A/I';
-- ALTER TABLE inventory_material ADD COLUMN original_name VARCHAR(200) NULL COMMENT '来源原名称，新模型必填',
--   ADD COLUMN original_specification VARCHAR(500) NULL COMMENT '来源原规格，原貌保留';
-- 按material_id/category_id从备份恢复对应列；两表按备份的CREATE TABLE/INSERT恢复；不覆盖其它业务列。
-- 不UPDATE/DELETE物料行，不触碰remark、分类确认、库存流水、余额或业务单据。
SELECT DATABASE() AS target_schema;

-- 删除已解除运行时依赖的迁移期第二真源；information_schema守卫使人工核查后的显式重试可重入。
SET @retire_ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material_category' AND COLUMN_NAME='material_type'),
 'ALTER TABLE inventory_material_category DROP COLUMN material_type', 'SELECT 1');
PREPARE retire_stmt FROM @retire_ddl;
EXECUTE retire_stmt;
DEALLOCATE PREPARE retire_stmt;

-- 删除已解除运行时依赖的迁移期第二真源；information_schema守卫使人工核查后的显式重试可重入。
SET @retire_ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='original_name'),
 'ALTER TABLE inventory_material DROP COLUMN original_name', 'SELECT 1');
PREPARE retire_stmt FROM @retire_ddl;
EXECUTE retire_stmt;
DEALLOCATE PREPARE retire_stmt;

-- 删除已解除运行时依赖的迁移期第二真源；information_schema守卫使人工核查后的显式重试可重入。
SET @retire_ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='original_specification'),
 'ALTER TABLE inventory_material DROP COLUMN original_specification', 'SELECT 1');
PREPARE retire_stmt FROM @retire_ddl;
EXECUTE retire_stmt;
DEALLOCATE PREPARE retire_stmt;

-- 302双表已无业务读写；用户授权丢弃旧定义/取值，不迁移、不保留第二属性真源。
DROP TABLE IF EXISTS inventory_material_spec_value;
DROP TABLE IF EXISTS inventory_material_spec_attr_def;

SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND
 ((TABLE_NAME='inventory_material_category' AND COLUMN_NAME='material_type')
 OR (TABLE_NAME='inventory_material' AND COLUMN_NAME IN ('original_name','original_specification')));
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME IN ('inventory_material_spec_attr_def','inventory_material_spec_value');
SELECT COUNT(*) AS total,SUM(category_id IS NULL) AS unclassified FROM inventory_material;
