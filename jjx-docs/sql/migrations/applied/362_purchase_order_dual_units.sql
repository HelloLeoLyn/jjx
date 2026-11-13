-- risk: low
-- dev-20261112-011：只加采购双单位与预估卷维度；不回填、不改库存/收货/财务数据。
-- quantity/unit沿用物料基本单位；折合单价由服务层只读计算，不建第二金额真源。

SET @dual_unit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='purchase_unit'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN purchase_unit varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''采购单位''');
PREPARE dual_unit_stmt FROM @dual_unit_ddl;
EXECUTE dual_unit_stmt;
DEALLOCATE PREPARE dual_unit_stmt;

SET @dual_unit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='price_unit'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN price_unit varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''价格单位''');
PREPARE dual_unit_stmt FROM @dual_unit_ddl;
EXECUTE dual_unit_stmt;
DEALLOCATE PREPARE dual_unit_stmt;

SET @dual_unit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='roll_count'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN roll_count int NULL COMMENT ''采购/计价卷数，不足整卷向上取整''');
PREPARE dual_unit_stmt FROM @dual_unit_ddl;
EXECUTE dual_unit_stmt;
DEALLOCATE PREPARE dual_unit_stmt;

SET @dual_unit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='roll_length_m'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN roll_length_m decimal(12,4) NULL COMMENT ''预估每卷长度m''');
PREPARE dual_unit_stmt FROM @dual_unit_ddl;
EXECUTE dual_unit_stmt;
DEALLOCATE PREPARE dual_unit_stmt;

SET @dual_unit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='roll_width_mm'), 'SELECT 1', 'ALTER TABLE purchase_order_item ADD COLUMN roll_width_mm decimal(12,2) NULL COMMENT ''预估卷宽mm''');
PREPARE dual_unit_stmt FROM @dual_unit_ddl;
EXECUTE dual_unit_stmt;
DEALLOCATE PREPARE dual_unit_stmt;
