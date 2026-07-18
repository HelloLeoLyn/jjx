-- ============================================================
-- 数据清理脚本 - 清除所有业务演示数据
-- 保留：超级管理员、部门、角色、菜单、字典
-- 执行时机：在初始化之前执行，或用于重置数据
-- 使用方法：source 00_clean_data.sql;
-- 或：mysql -u root -p jjx_db < 00_clean_data.sql
-- ============================================================

-- ============================================================
-- 警告：此脚本将删除所有业务数据，请谨慎执行！
-- ============================================================

SELECT '========================================' AS warning;
SELECT '⚠️  开始清理业务演示数据...' AS warning;
SELECT '========================================' AS warning;

-- ============================================================
-- 第1步：生产模块清理
-- ============================================================
SELECT '【1/6】清理生产模块...' AS progress;
DELETE FROM production_order;
SELECT CONCAT('  已删除生产订单: ', ROW_COUNT(), ' 条') AS result;

-- ============================================================
-- 第2步：采购模块清理
-- ============================================================
SELECT '【2/6】清理采购模块...' AS progress;
DELETE FROM purchase_supplier;
SELECT CONCAT('  已删除供应商: ', ROW_COUNT(), ' 条') AS result;

-- ============================================================
-- 第3步：销售模块清理
-- ============================================================
SELECT '【3/6】清理销售模块...' AS progress;
DELETE FROM sales_customer;
SELECT CONCAT('  已删除客户: ', ROW_COUNT(), ' 条') AS result;

-- ============================================================
-- 第4步：产品模块清理
-- ============================================================
SELECT '【4/6】清理产品模块...' AS progress;
DELETE FROM product_routing_item;
SELECT CONCAT('  已删除工艺路线明细: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM product_routing;
SELECT CONCAT('  已删除工艺路线: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM product_bom_item;
SELECT CONCAT('  已删除BOM明细: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM product_bom;
SELECT CONCAT('  已删除BOM: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM product;
SELECT CONCAT('  已删除产品: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM product_category;
SELECT CONCAT('  已删除产品分类: ', ROW_COUNT(), ' 条') AS result;

-- ============================================================
-- 第5步：库存模块清理
-- ============================================================
SELECT '【5/6】清理库存模块...' AS progress;
DELETE FROM inventory_material;
SELECT CONCAT('  已删除物料: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM inventory_material_category;
SELECT CONCAT('  已删除物料分类: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM inventory_storage_location;
SELECT CONCAT('  已删除库位: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM inventory_warehouse;
SELECT CONCAT('  已删除仓库: ', ROW_COUNT(), ' 条') AS result;

-- ============================================================
-- 第6步：系统模块清理（保留超级管理员）
-- ============================================================
SELECT '【6/6】清理系统模块（保留超级管理员）...' AS progress;
DELETE FROM sys_user_role WHERE user_id != 1;
SELECT CONCAT('  已删除非管理员角色关联: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM sys_user WHERE user_id != 1;
SELECT CONCAT('  已删除非管理员用户: ', ROW_COUNT(), ' 条') AS result;
DELETE FROM sys_role WHERE role_id != 1;
SELECT CONCAT('  已删除非管理员角色: ', ROW_COUNT(), ' 条') AS result;
-- 部门、菜单、字典保留不动
SELECT '  部门数据: 保留' AS result;
SELECT '  菜单数据: 保留' AS result;
SELECT '  字典数据: 保留' AS result;

-- ============================================================
-- 清理完成
-- ============================================================
SELECT '========================================' AS summary;
SELECT '✅ 业务演示数据清理完成！' AS summary;
SELECT '========================================' AS summary;
SELECT '保留的数据：' AS summary;
SELECT CONCAT('  部门: ', (SELECT COUNT(*) FROM sys_dept)) AS summary;
SELECT CONCAT('  角色: ', (SELECT COUNT(*) FROM sys_role)) AS summary;
SELECT CONCAT('  用户: ', (SELECT COUNT(*) FROM sys_user)) AS summary;
SELECT CONCAT('  菜单: ', (SELECT COUNT(*) FROM sys_menu)) AS summary;
SELECT CONCAT('  字典类型: ', (SELECT COUNT(*) FROM sys_dict)) AS summary;
SELECT CONCAT('  字典项: ', (SELECT COUNT(*) FROM sys_dict_item)) AS summary;
SELECT '========================================' AS summary;
SELECT '剩余账号: admin / admin123' AS summary;
SELECT '========================================' AS summary;
