-- 59_quality_template_print_impl_columns.sql
-- 质量记录模板注册表加打印实现跟踪字段（2026-09-04 用户拍板）
-- print_component: 前端打印实现组件/页面路径（空=未实现）
-- biz_module:      相关业务归属（如 采购管理-采购订单）
-- print_mode:      打印实现程度 blank=未实现 / system=系统版 / dual=系统版+纸版复刻
SET @need_1 = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_template_registry' AND COLUMN_NAME = 'print_component');
SET @stmt1 = IF(@need_1 > 0, 'ALTER TABLE quality_template_registry ADD COLUMN print_component varchar(200) NULL COMMENT ''前端打印实现组件/页面路径（空=未实现）''', 'DO 0');
PREPARE s1 FROM @stmt1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @need_2 = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_template_registry' AND COLUMN_NAME = 'biz_module');
SET @stmt2 = IF(@need_2 > 0, 'ALTER TABLE quality_template_registry ADD COLUMN biz_module varchar(100) NULL COMMENT ''相关业务归属（如 采购管理-采购订单）''', 'DO 0');
PREPARE s2 FROM @stmt2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @need_3 = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_template_registry' AND COLUMN_NAME = 'print_mode');
SET @stmt3 = IF(@need_3 > 0, 'ALTER TABLE quality_template_registry ADD COLUMN print_mode varchar(20) NULL COMMENT ''打印实现程度: blank未实现/system系统版/dual双版式''', 'DO 0');
PREPARE s3 FROM @stmt3; EXECUTE s3; DEALLOCATE PREPARE s3;

-- 存量初值（已知实现的行；其余留空待盘点）
UPDATE quality_template_registry
SET print_component = 'views/purchase/order/print.vue',
    biz_module = '采购管理-采购订单',
    print_mode = 'dual'
WHERE record_no = 'JJX-QR-024' AND print_component IS NULL;

UPDATE quality_template_registry
SET print_component = 'views/production/order/print.vue',
    biz_module = '生产管理-生产工单',
    print_mode = 'system'
WHERE record_no = 'JJX-QR-005' AND print_component IS NULL;

UPDATE quality_template_registry
SET print_component = 'views/sales/delivery/print.vue',
    biz_module = '销售管理-销售发货(送货)',
    print_mode = 'system'
WHERE record_no = 'JJX-QR-026' AND print_component IS NULL;

UPDATE quality_template_registry
SET print_component = 'views/inventory/outbound/print.vue',
    biz_module = '库存管理-生产领料出库',
    print_mode = 'system'
WHERE record_no = 'JJX-QR-031' AND print_component IS NULL;

UPDATE quality_template_registry
SET print_component = 'views/inventory/outbound/print.vue',
    biz_module = '库存管理-出库/出货',
    print_mode = 'system'
WHERE record_no = 'JJX-QR-057' AND print_component IS NULL;

UPDATE quality_template_registry
SET print_component = 'views/sales/inquiry/print.vue',
    biz_module = '销售管理-询价(样品需求单)',
    print_mode = 'system'
WHERE record_no = 'JJX-QR-065' AND print_component IS NULL;
