-- risk: medium
-- dev-20261113-002：采购录入与计价数量分离；只扩展采购数据，不回填/改库存。
-- 前置：用户手工备份后走db-migrate；363由原任务另行处理，本迁移不代清洗单位。
-- 原列含义与362五字段不变；计价数量不存，按采购数量/本单尺寸/单位重算。

SET @purchase_entry_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='purchase_quantity'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN purchase_quantity decimal(12,4) NULL COMMENT ''原始采购数量，quantity仍为库存基本数量''');
PREPARE purchase_entry_stmt FROM @purchase_entry_ddl;
EXECUTE purchase_entry_stmt;
DEALLOCATE PREPARE purchase_entry_stmt;

SET @purchase_entry_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='piece_width_mm'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN piece_width_mm decimal(12,2) NULL COMMENT ''本单每片宽度mm''');
PREPARE purchase_entry_stmt FROM @purchase_entry_ddl;
EXECUTE purchase_entry_stmt;
DEALLOCATE PREPARE purchase_entry_stmt;

SET @purchase_entry_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='piece_length_mm'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN piece_length_mm decimal(12,2) NULL COMMENT ''本单每片长度mm''');
PREPARE purchase_entry_stmt FROM @purchase_entry_ddl;
EXECUTE purchase_entry_stmt;
DEALLOCATE PREPARE purchase_entry_stmt;

SET @purchase_entry_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='purchase_remark'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN purchase_remark varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''采购加工交货说明，独立于检验备注''');
PREPARE purchase_entry_stmt FROM @purchase_entry_ddl;
EXECUTE purchase_entry_stmt;
DEALLOCATE PREPARE purchase_entry_stmt;

SET @purchase_entry_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='tax_rate'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN tax_rate decimal(5,2) NULL COMMENT ''本单行税率%，NULL表示历史未记录''');
PREPARE purchase_entry_stmt FROM @purchase_entry_ddl;
EXECUTE purchase_entry_stmt;
DEALLOCATE PREPARE purchase_entry_stmt;

SET @purchase_entry_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_supplier' AND column_name='material_purchase_defaults'), 'SELECT 1', 'ALTER TABLE purchase_supplier ADD COLUMN material_purchase_defaults json NULL COMMENT ''按物料ID保存默认采购单位和报价单位，无单价或库存余额''');
PREPARE purchase_entry_stmt FROM @purchase_entry_ddl;
EXECUTE purchase_entry_stmt;
DEALLOCATE PREPARE purchase_entry_stmt;

ALTER TABLE purchase_order_item MODIFY COLUMN unit_price decimal(14,4) NOT NULL COMMENT '按报价单位的单价，4位小数';
-- 已有数据不补猜采购数量、尺寸、税率和采购约定；旧单读取采用明确兼容路径。
