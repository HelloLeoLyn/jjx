-- ============================================================
-- 数据初始化主入口脚本
-- 执行顺序：按编号顺序执行
-- 使用方法：source init_all.sql;
-- 或：mysql -u root -p jjx_db < init_all.sql
-- ============================================================

-- ============================================================
-- 执行前请确认数据库已创建
-- CREATE DATABASE IF NOT EXISTS jjx_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE jjx_db;
-- ============================================================

-- ============================================================
-- 第1步：系统模块初始化
-- 包含：部门、用户、角色、菜单、字典
-- ============================================================
-- source 01_init_system.sql;
SELECT '【1/6】系统模块初始化开始...' AS progress;
INSERT IGNORE INTO sys_dept (dept_id, parent_id, dept_name, order_num, leader, phone, email, status, del_flag, create_by, create_time, update_by, update_time) VALUES
(100, 0, 'JJX科技', 1, '系统管理员', '13800138000', 'admin@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(101, 100, '管理部', 1, '管理员', '13800138001', 'admin@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(102, 100, '销售部', 2, '销售经理', '13800138002', 'sales@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(103, 100, '采购部', 3, '采购经理', '13800138003', 'purchase@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(104, 100, '生产部', 4, '生产经理', '13800138004', 'production@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(105, 100, '品质部', 5, '品质经理', '13800138005', 'quality@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(106, 100, '仓储部', 6, '仓管经理', '13800138006', 'warehouse@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(107, 100, '技术部', 7, '技术经理', '13800138007', 'tech@jjx.com', '0', '0', 'system', NOW(), 'system', NOW());
SELECT CONCAT('  部门数据: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, update_by, update_time, remark) VALUES
(1, '超级管理员', 'admin', 1, '1', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '超级管理员'),
(2, '销售员', 'sales', 2, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '销售员'),
(3, '采购员', 'purchase', 3, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '采购员'),
(4, '生产员', 'production', 4, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '生产员'),
(5, '质检员', 'quality', 5, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '质检员'),
(6, '仓管员', 'warehouse', 6, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '仓管员');
SELECT CONCAT('  角色数据: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phone, sex, avatar, password, salt, status, del_flag, create_by, create_time, update_by, update_time) VALUES
(1, 101, 'admin', '系统管理员', '00', 'admin@jjx.com', '13800138000', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(2, 102, 'sales', '销售员张三', '00', 'sales@jjx.com', '13800138002', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(3, 103, 'purchase', '采购员李四', '00', 'purchase@jjx.com', '13800138003', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(4, 104, 'production', '生产员王五', '00', 'production@jjx.com', '13800138004', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(5, 105, 'quality', '质检员赵六', '00', 'quality@jjx.com', '13800138005', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(6, 106, 'warehouse', '仓管员孙七', '00', 'warehouse@jjx.com', '13800138006', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(7, 107, 'tech', '技术员周八', '00', 'tech@jjx.com', '13800138007', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW());
SELECT CONCAT('  用户数据: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 5), (6, 6), (7, 1);
SELECT CONCAT('  用户角色关联: ', ROW_COUNT(), ' 条已插入') AS result;

-- 菜单数据（简化版，仅插入一级菜单）
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(1, '系统管理', 0, 1, 'system', '', '1', '0', 'M', '0', '0', '', 'system', 'system', NOW(), 'system', NOW(), '系统管理目录'),
(2, '销售管理', 0, 2, 'sales', '', '1', '0', 'M', '0', '0', '', 'sales-order', 'system', NOW(), 'system', NOW(), '销售管理目录'),
(3, '采购管理', 0, 3, 'purchase', '', '1', '0', 'M', '0', '0', '', 'purchase', 'system', NOW(), 'system', NOW(), '采购管理目录'),
(4, '生产管理', 0, 4, 'production', '', '1', '0', 'M', '0', '0', '', 'production', 'system', NOW(), 'system', NOW(), '生产管理目录'),
(5, '库存管理', 0, 5, 'inventory', '', '1', '0', 'M', '0', '0', '', 'inventory', 'system', NOW(), 'system', NOW(), '库存管理目录'),
(6, '产品管理', 0, 6, 'product', '', '1', '0', 'M', '0', '0', '', 'product', 'system', NOW(), 'system', NOW(), '产品管理目录'),
(7, '报表统计', 0, 7, 'report', '', '1', '0', 'M', '0', '0', '', 'report', 'system', NOW(), 'system', NOW(), '报表统计目录');
SELECT CONCAT('  菜单数据: ', ROW_COUNT(), ' 条已插入') AS result;

-- 字典数据
INSERT IGNORE INTO sys_dict (dict_id, dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, '通用状态', 'sys_normal_status', '0', 'system', NOW(), 'system', NOW(), '通用状态字典'),
(2, '性别', 'sys_user_sex', '0', 'system', NOW(), 'system', NOW(), '性别字典'),
(3, '是否', 'sys_yes_no', '0', 'system', NOW(), 'system', NOW(), '是否字典'),
(4, '物料类型', 'material_type', '0', 'system', NOW(), 'system', NOW(), '物料类型字典'),
(5, '工序类型', 'process_type', '0', 'system', NOW(), 'system', NOW(), '标准工序类型字典'),
(6, '工序类别', 'process_category', '0', 'system', NOW(), 'system', NOW(), '标准工序类别字典'),
(7, '仓库类型', 'warehouse_type', '0', 'system', NOW(), 'system', NOW(), '仓库类型字典'),
(8, '产品状态', 'product_status', '0', 'system', NOW(), 'system', NOW(), '产品状态字典'),
(9, '审核状态', 'approve_status', '0', 'system', NOW(), 'system', NOW(), '审核状态字典');
SELECT CONCAT('  字典类型: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO sys_dict_item (dict_item_id, dict_id, dict_code, dict_label, sort_order, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, 1, '0', '正常', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(2, 1, '1', '停用', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(3, 2, '0', '男', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(4, 2, '1', '女', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(5, 2, '2', '未知', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(6, 3, '0', '否', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(7, 3, '1', '是', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(8, 4, 'R', '原材料', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(9, 4, 'S', '半成品', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(10, 4, 'F', '成品', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(11, 4, 'A', '辅助材料', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
(12, 5, 'PRINTING', '印刷', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(13, 5, 'CUTTING', '模切', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(14, 5, 'LAMINATING', '贴合', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(15, 5, 'TESTING', '测试', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
(16, 5, 'PACKAGING', '包装', 5, '0', 'system', NOW(), 'system', NOW(), NULL),
(17, 6, 'PREPARATION', '准备', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(18, 6, 'MAIN', '主要', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(19, 6, 'FINISHING', '后处理', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(20, 6, 'QUALITY', '质量', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
(21, 7, 'normal', '普通仓库', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(22, 7, 'quality', '质检仓库', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(23, 7, 'finished', '成品仓库', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(24, 7, 'scrap', '废品仓库', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
(25, 8, '0', '开发中', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(26, 8, '1', '已发布', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(27, 8, '2', '已停产', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(28, 9, '0', '待审核', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(29, 9, '1', '已通过', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(30, 9, '2', '已驳回', 3, '0', 'system', NOW(), 'system', NOW(), NULL);
SELECT CONCAT('  字典数据项: ', ROW_COUNT(), ' 条已插入') AS result;
SELECT '【1/6】系统模块初始化完成 ✅' AS progress;

-- ============================================================
-- 第2步：库存模块初始化
-- 包含：仓库、库位、物料分类、物料
-- ============================================================
SELECT '【2/6】库存模块初始化开始...' AS progress;

INSERT IGNORE INTO inventory_warehouse (warehouse_id, warehouse_code, warehouse_name, warehouse_type, location, manager, contact_phone, sort_order, status, create_by, create_time, update_by, update_time) VALUES
(1, 'WH-RAW', '原材料仓', 'normal', 'A栋1楼', '孙七', '13800138006', 1, '0', 'system', NOW(), 'system', NOW()),
(2, 'WH-SEMI', '半成品仓', 'normal', 'A栋2楼', '孙七', '13800138006', 2, '0', 'system', NOW(), 'system', NOW()),
(3, 'WH-FIN', '成品仓', 'finished', 'A栋3楼', '孙七', '13800138006', 3, '0', 'system', NOW(), 'system', NOW()),
(4, 'WH-QC', '质检仓', 'quality', 'B栋1楼', '赵六', '13800138005', 4, '0', 'system', NOW(), 'system', NOW()),
(5, 'WH-SCRAP', '废品仓', 'scrap', 'B栋2楼', '赵六', '13800138005', 5, '0', 'system', NOW(), 'system', NOW());
SELECT CONCAT('  仓库数据: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO inventory_storage_location (location_id, warehouse_id, location_code, location_name, location_type, area_code, aisle, shelf, layer, max_capacity, current_usage, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, 1, 'RAW-A-01-01', 'A区01架01层', 'shelf', 'A', '01', '01', '01', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(2, 1, 'RAW-A-01-02', 'A区01架02层', 'shelf', 'A', '01', '01', '02', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(3, 1, 'RAW-A-01-03', 'A区01架03层', 'shelf', 'A', '01', '01', '03', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(4, 1, 'RAW-A-02-01', 'A区02架01层', 'shelf', 'A', '01', '02', '01', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(5, 1, 'RAW-A-02-02', 'A区02架02层', 'shelf', 'A', '01', '02', '02', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(6, 1, 'RAW-B-01-01', 'B区01架01层', 'shelf', 'B', '02', '01', '01', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(7, 1, 'RAW-B-01-02', 'B区01架02层', 'shelf', 'B', '02', '01', '02', 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(8, 2, 'SEMI-A-01-01', 'A区01架01层', 'shelf', 'A', '01', '01', '01', 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(9, 2, 'SEMI-A-01-02', 'A区01架02层', 'shelf', 'A', '01', '01', '02', 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(10, 2, 'SEMI-B-01-01', 'B区01架01层', 'shelf', 'B', '02', '01', '01', 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(11, 3, 'FIN-A-01-01', 'A区01架01层', 'shelf', 'A', '01', '01', '01', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(12, 3, 'FIN-A-01-02', 'A区01架02层', 'shelf', 'A', '01', '01', '02', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(13, 3, 'FIN-A-02-01', 'A区02架01层', 'shelf', 'A', '01', '02', '01', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(14, 3, 'FIN-B-01-01', 'B区01架01层', 'shelf', 'B', '02', '01', '01', 2000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL),
(15, 4, 'QC-A-01-01', '待检区01', 'area', 'A', '01', NULL, NULL, 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), '待检验物料暂存区'),
(16, 4, 'QC-A-01-02', '不合格区01', 'area', 'A', '01', NULL, NULL, 500.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), '不合格品暂存区'),
(17, 5, 'SCRAP-A-01-01', '废品区01', 'area', 'A', '01', NULL, NULL, 1000.0000, 0.0000, '0', 'system', NOW(), 'system', NOW(), NULL);
SELECT CONCAT('  库位数据: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO inventory_material_category (category_id, category_code, category_name, parent_id, category_level, category_path, sort_order, status, create_by, create_time, update_by, update_time) VALUES
(1, 'BASE', '基材类', 0, 1, '/1', 1, '0', 'system', NOW(), 'system', NOW()),
(2, 'INK', '油墨类', 0, 1, '/2', 2, '0', 'system', NOW(), 'system', NOW()),
(3, 'ADH', '胶粘类', 0, 1, '/3', 3, '0', 'system', NOW(), 'system', NOW()),
(4, 'ELEC', '电子元件类', 0, 1, '/4', 4, '0', 'system', NOW(), 'system', NOW()),
(5, 'PACK', '包装材料类', 0, 1, '/5', 5, '0', 'system', NOW(), 'system', NOW()),
(6, 'AUX', '辅助材料类', 0, 1, '/6', 6, '0', 'system', NOW(), 'system', NOW()),
(11, 'PET', 'PET基材', 1, 2, '/1/11', 1, '0', 'system', NOW(), 'system', NOW()),
(12, 'PC', 'PC基材', 1, 2, '/1/12', 2, '0', 'system', NOW(), 'system', NOW()),
(13, 'PVC', 'PVC基材', 1, 2, '/1/13', 3, '0', 'system', NOW(), 'system', NOW()),
(21, 'SILVER', '导电银浆', 2, 2, '/2/21', 1, '0', 'system', NOW(), 'system', NOW()),
(22, 'INSULATE', '绝缘油墨', 2, 2, '/2/22', 2, '0', 'system', NOW(), 'system', NOW()),
(23, 'CARBON', '碳浆', 2, 2, '/2/23', 3, '0', 'system', NOW(), 'system', NOW()),
(24, 'UV', 'UV油墨', 2, 2, '/2/24', 4, '0', 'system', NOW(), 'system', NOW()),
(25, 'COLOR', '彩色油墨', 2, 2, '/2/25', 5, '0', 'system', NOW(), 'system', NOW()),
(31, '3M', '3M胶带', 3, 2, '/3/31', 1, '0', 'system', NOW(), 'system', NOW()),
(32, 'TESA', 'TESA胶带', 3, 2, '/3/32', 2, '0', 'system', NOW(), 'system', NOW()),
(33, 'NITTO', '日东胶带', 3, 2, '/3/33', 3, '0', 'system', NOW(), 'system', NOW()),
(41, 'LED', 'LED灯珠', 4, 2, '/4/41', 1, '0', 'system', NOW(), 'system', NOW()),
(42, 'CONN', '连接器', 4, 2, '/4/42', 2, '0', 'system', NOW(), 'system', NOW()),
(43, 'RES', '电阻', 4, 2, '/4/43', 3, '0', 'system', NOW(), 'system', NOW()),
(51, 'BAG', '包装袋', 5, 2, '/5/51', 1, '0', 'system', NOW(), 'system', NOW()),
(52, 'BOX', '包装盒/箱', 5, 2, '/5/52', 2, '0', 'system', NOW(), 'system', NOW()),
(53, 'FOAM', '缓冲材料', 5, 2, '/5/53', 3, '0', 'system', NOW(), 'system', NOW()),
(61, 'SCREEN', '网版', 6, 2, '/6/61', 1, '0', 'system', NOW(), 'system', NOW()),
(62, 'FILM', '菲林', 6, 2, '/6/62', 2, '0', 'system', NOW(), 'system', NOW()),
(63, 'TOOL', '模具/刀具', 6, 2, '/6/63', 3, '0', 'system', NOW(), 'system', NOW()),
(64, 'CHEM', '化学品', 6, 2, '/6/64', 4, '0', 'system', NOW(), 'system', NOW());
SELECT CONCAT('  物料分类: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO inventory_material (material_id, material_code, material_name, material_name_en, material_type, category_id, specification, unit, unit_conv, unit_alt, batch_control, shelf_life, expiry_alert_days, safe_stock, max_stock, reorder_point, standard_price, lead_time, supplier_id, supplier_name, default_warehouse_id, default_location_id, status, process_group, create_by, create_time, update_by, update_time) VALUES
(1, 'PET-0125', 'PET基材0.125mm', 'PET Film 0.125mm', 'R', 11, '0.125mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 100.0000, 1000.0000, 200.0000, 25.0000, 7, NULL, NULL, 1, 1, 0, NULL, 'system', NOW(), 'system', NOW()),
(2, 'PET-0188', 'PET基材0.188mm', 'PET Film 0.188mm', 'R', 11, '0.188mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 100.0000, 1000.0000, 200.0000, 30.0000, 7, NULL, NULL, 1, 1, 0, NULL, 'system', NOW(), 'system', NOW()),
(3, 'PET-0250', 'PET基材0.25mm', 'PET Film 0.25mm', 'R', 11, '0.25mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 80.0000, 800.0000, 150.0000, 35.0000, 7, NULL, NULL, 1, 1, 0, NULL, 'system', NOW(), 'system', NOW()),
(4, 'PC-0125', 'PC基材0.125mm', 'PC Film 0.125mm', 'R', 12, '0.125mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 50.0000, 500.0000, 100.0000, 45.0000, 10, NULL, NULL, 1, 2, 0, NULL, 'system', NOW(), 'system', NOW()),
(5, 'PC-0250', 'PC基材0.25mm', 'PC Film 0.25mm', 'R', 12, '0.25mm×500mm×200m', 'm²', 1.0000, NULL, 1, 365, 30, 50.0000, 500.0000, 100.0000, 55.0000, 10, NULL, NULL, 1, 2, 0, NULL, 'system', NOW(), 'system', NOW()),
(6, 'SILVER-A', '导电银浆A型', 'Silver Conductive Paste Type A', 'R', 21, '1kg/瓶', 'kg', 1.0000, NULL, 1, 180, 15, 10.0000, 100.0000, 20.0000, 850.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(7, 'SILVER-B', '导电银浆B型', 'Silver Conductive Paste Type B', 'R', 21, '1kg/瓶', 'kg', 1.0000, NULL, 1, 180, 15, 5.0000, 50.0000, 10.0000, 920.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(8, 'INS-WHITE', '白色绝缘油墨', 'White Insulation Ink', 'R', 22, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 5.0000, 50.0000, 10.0000, 120.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(9, 'INS-YELLOW', '黄色绝缘油墨', 'Yellow Insulation Ink', 'R', 22, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 3.0000, 30.0000, 5.0000, 130.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(10, 'CARBON-BLK', '黑色碳浆', 'Black Carbon Paste', 'R', 23, '1kg/瓶', 'kg', 1.0000, NULL, 1, 180, 15, 3.0000, 30.0000, 5.0000, 280.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(11, 'UV-CLEAR', 'UV光油', 'UV Varnish', 'R', 24, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 2.0000, 20.0000, 5.0000, 95.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(12, 'INK-BLACK', '黑色油墨', 'Black Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 2.0000, 20.0000, 5.0000, 65.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(13, 'INK-WHITE', '白色油墨', 'White Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 2.0000, 20.0000, 5.0000, 60.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(14, 'INK-RED', '红色油墨', 'Red Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 1.0000, 10.0000, 2.0000, 70.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(15, 'INK-BLUE', '蓝色油墨', 'Blue Ink', 'R', 25, '1kg/罐', 'kg', 1.0000, NULL, 1, 365, 30, 1.0000, 10.0000, 2.0000, 70.0000, 14, NULL, NULL, 1, 3, 0, NULL, 'system', NOW(), 'system', NOW()),
(16, '3M-467', '3M 467胶带', '3M 467 Adhesive', 'R', 31, '200mm×50m', 'm²', 1.0000, NULL, 1, 730, 60, 20.0000, 200.0000, 40.0000, 180.0000, 7, NULL, NULL, 1, 4, 0, NULL, 'system', NOW(), 'system', NOW()),
(17, '3M-468', '3M 468胶带', '3M 468 Adhesive', 'R', 31, '200mm×50m', 'm²', 1.0000, NULL, 1, 730, 60, 20.0000, 200.0000, 40.0000, 220.0000, 7, NULL, NULL, 1, 4, 0, NULL, 'system', NOW(), 'system', NOW()),
(18, 'TESA-4965', 'TESA 4965胶带', 'TESA 4965 Adhesive', 'R', 32, '200mm×50m', 'm²', 1.0000, NULL, 1, 730, 60, 10.0000, 100.0000, 20.0000, 195.0000, 10, NULL, NULL, 1, 4, 0, NULL, 'system', NOW(), 'system', NOW()),
(19, 'LED-0603', '0603 LED灯珠', '0603 LED', 'R', 41, '0603/蓝色', 'pcs', 1000.0000, 'Kpcs', 1, 1095, 90, 5000.0000, 50000.0000, 10000.0000, 0.0800, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
(20, 'LED-0805', '0805 LED灯珠', '0805 LED', 'R', 41, '0805/绿色', 'pcs', 1000.0000, 'Kpcs', 1, 1095, 90, 3000.0000, 30000.0000, 5000.0000, 0.1200, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
(21, 'CONN-ZIF', 'ZIF连接器', 'ZIF Connector', 'R', 42, '8pin/0.5mm间距', 'pcs', 100.0000, NULL, 0, NULL, NULL, 1000.0000, 10000.0000, 2000.0000, 0.5000, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
(22, 'CONN-FPC', 'FPC连接器', 'FPC Connector', 'R', 42, '12pin/0.5mm间距', 'pcs', 100.0000, NULL, 0, NULL, NULL, 1000.0000, 10000.0000, 2000.0000, 0.6500, 14, NULL, NULL, 1, 5, 0, NULL, 'system', NOW(), 'system', NOW()),
(23, 'BAG-ANTI', '防静电包装袋', 'Anti-static Bag', 'R', 51, '300×400mm', 'pcs', 100.0000, NULL, 0, NULL, NULL, 500.0000, 5000.0000, 1000.0000, 0.3500, 7, NULL, NULL, 1, 6, 0, NULL, 'system', NOW(), 'system', NOW()),
(24, 'BOX-CORR', '瓦楞纸箱', 'Corrugated Box', 'R', 52, '400×300×200mm', 'pcs', 1.0000, NULL, 0, NULL, NULL, 50.0000, 500.0000, 100.0000, 3.5000, 5, NULL, NULL, 1, 6, 0, NULL, 'system', NOW(), 'system', NOW()),
(25, 'FOAM-PE', 'PE珍珠棉', 'PE Foam', 'R', 53, '5mm×1m×100m', 'm²', 1.0000, NULL, 0, NULL, NULL, 20.0000, 200.0000, 40.0000, 8.0000, 5, NULL, NULL, 1, 6, 0, NULL, 'system', NOW(), 'system', NOW()),
(26, 'SCREEN-250', '250目网版', '250 Mesh Screen', 'A', 61, '250目/500×500mm', 'pcs', 1.0000, NULL, 0, NULL, NULL, 5.0000, 50.0000, 10.0000, 85.0000, 7, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(27, 'SCREEN-300', '300目网版', '300 Mesh Screen', 'A', 61, '300目/500×500mm', 'pcs', 1.0000, NULL, 0, NULL, NULL, 5.0000, 50.0000, 10.0000, 95.0000, 7, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(28, 'FILM-POS', '阳片菲林', 'Positive Film', 'A', 62, 'A4尺寸', 'pcs', 1.0000, NULL, 0, NULL, NULL, 10.0000, 100.0000, 20.0000, 25.0000, 3, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(29, 'CHEM-ALC', '无水酒精', 'Anhydrous Alcohol', 'A', 64, '500ml/瓶', '瓶', 1.0000, NULL, 1, 730, 60, 10.0000, 100.0000, 20.0000, 15.0000, 3, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW()),
(30, 'CHEM-THIN', '稀释剂', 'Thinner', 'A', 64, '1L/瓶', '瓶', 1.0000, NULL, 1, 730, 60, 5.0000, 50.0000, 10.0000, 28.0000, 3, NULL, NULL, 1, 7, 0, NULL, 'system', NOW(), 'system', NOW());
SELECT CONCAT('  物料数据: ', ROW_COUNT(), ' 条已插入') AS result;
SELECT '【2/6】库存模块初始化完成 ✅' AS progress;

-- ============================================================
-- 第3步：产品模块初始化
-- 包含：产品分类、产品、BOM、BOM明细、工艺路线、工艺路线明细
-- ============================================================
SELECT '【3/6】产品模块初始化开始...' AS progress;

INSERT IGNORE INTO product_category (category_id, category_code, category_name, parent_id, category_level, sort_order, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, 'FS', '薄膜开关', 0, 1, 1, '0', 'system', NOW(), 'system', NOW(), '薄膜开关类产品'),
(2, 'NP', '铭板', 0, 1, 2, '0', 'system', NOW(), 'system', NOW(), '铭板类产品'),
(3, 'PL', '面板', 0, 1, 3, '0', 'system', NOW(), 'system', NOW(), '面板类产品'),
(4, 'LB', '标签', 0, 1, 4, '0', 'system', NOW(), 'system', NOW(), '标签类产品'),
(11, 'FS-MEM', '薄膜开关-按键型', 1, 2, 1, '0', 'system', NOW(), 'system', NOW(), '按键式薄膜开关'),
(12, 'FS-TOUCH', '薄膜开关-触摸型', 1, 2, 2, '0', 'system', NOW(), 'system', NOW(), '触摸式薄膜开关'),
(13, 'FS-BACK', '薄膜开关-背光型', 1, 2, 3, '0', 'system', NOW(), 'system', NOW(), '带背光薄膜开关'),
(21, 'NP-MET', '金属铭板', 2, 2, 1, '0', 'system', NOW(), 'system', NOW(), '金属材质铭板'),
(22, 'NP-PLASTIC', '塑料铭板', 2, 2, 2, '0', 'system', NOW(), 'system', NOW(), '塑料材质铭板');
SELECT CONCAT('  产品分类: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO product (product_id, product_code, product_name, category_id, product_type, spec_json, base_price, cost_price, min_order_qty, lead_time, product_status, current_bom_id, current_route_id, create_by, create_time, update_by, update_time, remark, unit) VALUES
(1, 'FS-2024-001', '6键薄膜开关面板', 11, 'standard', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6,"keyType":"snap","circuitType":"single","connector":"ZIF-8pin","voltage":"12V","current":"100mA","lifeCycle":"100万次","operatingForce":"1.5-3.0N","operatingTemp":"-20~70℃"}', 15.0000, 8.5000, 100, 15, 1, NULL, NULL, 'system', NOW(), 'system', NOW(), '标准6键薄膜开关面板，适用于工业控制设备', 'pcs'),
(2, 'FS-2024-002', '12键背光薄膜开关', 13, 'standard', '{"dimensions":{"width":120,"height":160,"unit":"mm"},"keyCount":12,"keyType":"snap","circuitType":"double","connector":"FPC-12pin","voltage":"12V","current":"200mA","lifeCycle":"100万次","operatingForce":"1.5-3.0N","operatingTemp":"-20~70℃","backlight":"LED-blue"}', 28.0000, 16.5000, 50, 20, 1, NULL, NULL, 'system', NOW(), 'system', NOW(), '12键带蓝色背光薄膜开关，适用于医疗设备', 'pcs'),
(3, 'FS-2024-003', '4键触摸薄膜开关', 12, 'standard', '{"dimensions":{"width":60,"height":100,"unit":"mm"},"keyCount":4,"keyType":"touch","circuitType":"single","connector":"ZIF-6pin","voltage":"5V","current":"50mA","lifeCycle":"500万次","operatingTemp":"-20~70℃","touchSensitivity":"可调"}', 22.0000, 12.0000, 100, 20, 0, NULL, NULL, 'system', NOW(), 'system', NOW(), '4键触摸式薄膜开关，适用于智能家居设备', 'pcs');
SELECT CONCAT('  产品数据: ', ROW_COUNT(), ' 条已插入') AS result;

INSERT IGNORE INTO product_bom (bom_id, bom_code, bom_name, bom_type, bom_version, product_id, approve_status, approve_remark, is_current, create_by, create_time, update_by, update_time, remark, effective_date, expiry_date) VALUES
(1, 'BOM-FS001-V1', '6键薄膜开关BOM V1.0', 'manufacturing', 'V1.0', 1, 1, NULL, 1, 'system', NOW(), 'system', NOW(), '6键薄膜开关标准BOM', '2024-01-01 00:00:00', '2025-12-31 00:00:00'),
(2, 'BOM-FS002-V1', '12键背光薄膜开关BOM V1.0', 'manufacturing', 'V1.0', 2, 1, NULL, 1, 'system', NOW(), 'system', NOW(), '12键背光薄膜开关标准BOM', '2024-01-01 00:00:00', '2025-12-31 00:00:00'),
(3, 'BOM-FS003-V1', '4键触摸薄膜开关BOM V1.0', 'manufacturing', 'V1.0', 3, 0, NULL, 1, 'system', NOW(), 'system', NOW(), '4键触摸薄膜开关标准BOM', '2024-01-01 00:00:00', '2025-12-31 00:00:00');
SELECT CONCAT('  BOM数据: ', ROW_COUNT(), ' 条已插入') AS result;

UPDATE product SET current_bom_id = 1 WHERE product_id = 1;
UPDATE product SET current_bom_id = 2 WHERE product_id = 2;
UPDATE product SET current_bom_id = 3 WHERE product_id = 3;
SELECT '  产品BOM关联已更新' AS result;

INSERT IGNORE INTO product_routing (routing_id, routing_code, routing_name, routing_type, product_id, product_code, product_name, routing_version, is_current, approve_status, total_labor_hours, total_machine_hours, process_count, description, remark, create_by, create_time, update_by, update_time) VALUES
(1, 'RT-FS001-V1', '6键薄膜开关工艺路线 V1.0', 0, 1, 'FS-2024-001', '6键薄膜开关面板', 'V1.0', 1, 1, 6.30, 5.30, 12, '6键薄膜开关标准生产工艺路线', NULL, 'system', NOW(), 'system', NOW()),
(2, 'RT-FS002-V1', '12键背光薄膜开关工艺路线 V1.0', 0, 2, 'FS-2024-002', '12键背光薄膜开关', 'V1.0', 1, 1, 8.80, 7.80, 14, '12键背光薄膜开关标准生产工艺路线', NULL, 'system', NOW(), 'system', NOW()),
(3, 'RT-FS003-V1', '4键触摸薄膜开关工艺路线 V1.0', 0, 3, 'FS-2024-003', '4键触摸薄膜开关', 'V1.0', 1, 0, 5.80, 4.80, 11, '4键触摸薄膜开关标准生产工艺路线', NULL, 'system', NOW(), 'system', NOW());
SELECT CONCAT('  工艺路线: ', ROW_COUNT(), ' 条已插入') AS result;

UPDATE product SET current_route_id = 1 WHERE product_id = 1;
UPDATE product SET current_route_id = 2 WHERE product_id = 2;
UPDATE product SET current_route_id = 3 WHERE product_id = 3;
SELECT '  产品工艺路线关联已更新' AS result;
SELECT '【3/6】产品模块初始化完成 ✅' AS progress;

-- ============================================================
-- 第4步：销售模块初始化
-- 包含：客户数据
-- ============================================================
SELECT '【4/6】销售模块初始化开始...' AS progress;

INSERT IGNORE INTO sales_customer (customer_id, customer_code, customer_name, customer_short_name, customer_type, customer_level, industry_category, customer_source, country, province, city, address, postal_code, contact_person, contact_phone, contact_email, fax, website, unified_social_credit_code, taxpayer_id, bank_name, bank_account, payment_method, payment_terms, credit_limit, used_credit_limit, customer_status, cooperation_start_date, cooperation_end_date, sales_manager_id, sales_manager_name, remark, customer_score, annual_purchase_amount, main_product_demand, special_requirements, is_vip, customer_tags, attachments, deleted, create_by, create_time, update_by, update_time) VALUES
(1, 'CUS-2024-001', '深圳华强电子科技有限公司', '华强电子', 1, 1, '工业控制', 2, '中国', '广东省', '深圳市', '深圳市南山区科技园南区R2-B栋8楼', '518057', '李明', '13912345678', 'liming@hqelec.com', '0755-12345678', 'www.hqelec.com', '91440300MA5XXXXXX1', '91440300MA5XXXXXX1', '中国工商银行深圳科技园支行', '400002XXXX9200XXXXXX', 3, '月结30天', 500000.00, 0.00, 2, '2024-01-15 00:00:00', NULL, 2, '销售员张三', '优质客户，长期合作', 5, 1200000.00, '薄膜开关、铭板', '需要提供ROHS报告', 1, '["VIP","工业控制","长期合作"]', NULL, 0, 'system', NOW(), 'system', NOW()),
(2, 'CUS-2024-002', '杭州迈瑞医疗设备有限公司', '迈瑞医疗', 1, 1, '医疗器械', 3, '中国', '浙江省', '杭州市', '杭州市滨江区滨康路567号', '310052', '王芳', '13923456789', 'wangfang@mindray.com', '0571-87654321', 'www.mindray.com', '91330100MA5XXXXXX2', '91330100MA5XXXXXX2', '中国建设银行杭州滨江支行', '400002XXXX9200XXXXXX', 3, '月结60天', 800000.00, 0.00, 2, '2024-03-01 00:00:00', NULL, 2, '销售员张三', '医疗行业重点客户', 5, 2000000.00, '背光薄膜开关、触摸薄膜开关', '医疗级认证要求，需提供生物相容性报告', 1, '["VIP","医疗器械","高要求"]', NULL, 0, 'system', NOW(), 'system', NOW()),
(3, 'CUS-2024-003', '上海智能家居科技有限公司', '智能家居', 1, 2, '智能家居', 1, '中国', '上海市', '上海市', '上海市浦东新区张江高科技园区碧波路888号', '201203', '张伟', '13934567890', 'zhangwei@smarthome.com', '021-55556666', 'www.smarthome.com', '91310000MA5XXXXXX3', '91310000MA5XXXXXX3', '中国银行上海张江支行', '400002XXXX9200XXXXXX', 3, '月结30天', 300000.00, 0.00, 2, '2024-05-20 00:00:00', NULL, 2, '销售员张三', '新兴行业客户', 3, 500000.00, '触摸薄膜开关、面板', '需要支持定制化设计', 0, '["智能家居","定制需求"]', NULL, 0, 'system', NOW(), 'system', NOW()),
(4, 'CUS-2024-004', '东莞精工机械设备有限公司', '精工机械', 1, 2, '工业设备', 2, '中国', '广东省', '东莞市', '东莞市长安镇乌沙社区振安中路88号', '523850', '陈强', '13945678901', 'chenqiang@jingong.com', '0769-85321234', 'www.jingong.com', '91441900MA5XXXXXX4', '91441900MA5XXXXXX4', '中国农业银行东莞长安支行', '400002XXXX9200XXXXXX', 2, '货到付款', 200000.00, 0.00, 2, '2024-06-10 00:00:00', NULL, 2, '销售员张三', '稳定合作客户', 3, 300000.00, '薄膜开关、铭板', '交期要求严格', 0, '["工业设备","交期敏感"]', NULL, 0, 'system', NOW(), 'system', NOW()),
(5, 'CUS-2024-005', 'Techtronix International Ltd.', 'Techtronix', 2, 1, '电子制造', 1, '美国', 'California', 'San Jose', '2001 Gateway Place, Suite 100, San Jose, CA 95110', '95110', 'John Smith', '+1-408-555-0100', 'john.smith@techtronix.com', '+1-408-555-0199', 'www.techtronix.com', NULL, NULL, 'Bank of America', 'XXXX-XXXX-XXXX-XXXX', 4, '月结60天', 1000000.00, 0.00, 2, '2024-02-01 00:00:00', NULL, 2, '销售员张三', '海外重点客户', 5, 3000000.00, '薄膜开关、铭板、面板', '需要UL认证，出口包装要求', 1, '["VIP","海外","电子制造"]', NULL, 0, 'system', NOW(), 'system', NOW());
SELECT CONCAT('  客户数据: ', ROW_COUNT(), ' 条已插入') AS result;
SELECT '【4/6】销售模块初始化完成 ✅' AS progress;

-- ============================================================
-- 第5步：采购模块初始化
-- 包含：供应商数据
-- ============================================================
SELECT '【5/6】采购模块初始化开始...' AS progress;

INSERT IGNORE INTO purchase_supplier (supplier_id, supplier_code, supplier_name, supplier_type, contact_person, phone, email, address, payment_terms, bank_account, tax_number, evaluation_score, quality_score, delivery_score, price_score, last_evaluation_date, status, del_flag, create_by, create_time, update_by, update_time, remark) VALUES
(1, 'SUP-2024-001', '东莞华美PET材料有限公司', 'material', '刘经理', '0769-81112222', 'liu@huamei.com', '东莞市寮步镇华南工业区金富路88号', '月结30天', '400002XXXX9200XXXX11', '91441900MA5XXXXXXA1', 92.00, 90.00, 95.00, 88.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), 'PET基材主要供应商，品质稳定'),
(2, 'SUP-2024-002', '深圳银科电子材料有限公司', 'material', '陈经理', '0755-26553333', 'chen@yinke.com', '深圳市宝安区西乡街道固戍社区固戍一路88号', '月结30天', '400002XXXX9200XXXX22', '91440300MA5XXXXXXA2', 95.00, 93.00, 96.00, 92.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '导电银浆主要供应商，技术领先'),
(3, 'SUP-2024-003', '上海3M胶带有限公司', 'material', '王经理', '021-68886666', 'wang@3m.com.cn', '上海市闵行区田林路888号', '月结45天', '400002XXXX9200XXXX33', '91310000MA5XXXXXXA3', 96.00, 95.00, 95.00, 90.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '3M胶带官方授权经销商'),
(4, 'SUP-2024-004', '深圳华强电子元器件有限公司', 'material', '赵经理', '0755-83665555', 'zhao@hqelec.com', '深圳市福田区华强北路华强电子世界3楼', '月结15天', '400002XXXX9200XXXX44', '91440300MA5XXXXXXA4', 88.00, 85.00, 90.00, 85.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '电子元器件供应商，品类齐全'),
(5, 'SUP-2024-005', '广州彩印包装材料有限公司', 'material', '黄经理', '020-82228888', 'huang@caiyin.com', '广州市番禺区石碁镇市莲路88号', '月结30天', '400002XXXX9200XXXX55', '91440100MA5XXXXXXA5', 85.00, 82.00, 88.00, 80.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '包装材料供应商'),
(6, 'SUP-2024-006', '东莞精工模具制造有限公司', 'equipment', '周经理', '0769-85339999', 'zhou@jingongmold.com', '东莞市长安镇乌沙社区振安路168号', '月结30天', '400002XXXX9200XXXX66', '91441900MA5XXXXXXA6', 90.00, 88.00, 92.00, 85.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '模切模具供应商'),
(7, 'SUP-2024-007', '日本油墨化工（广州）有限公司', 'material', '渡边一郎', '020-83337777', 'watanabe@japank.com', '广州市黄埔区经济技术开发区东区', '月结60天', '400002XXXX9200XXXX77', '91440100MA5XXXXXXA7', 93.00, 94.00, 90.00, 88.00, '2024-06-01', 0, '0', 'system', NOW(), 'system', NOW(), '进口油墨供应商，品质优良');
SELECT CONCAT('  供应商数据: ', ROW_COUNT(), ' 条已插入') AS result;
SELECT '【5/6】采购模块初始化完成 ✅' AS progress;

-- ============================================================
-- 第6步：生产模块初始化
-- 包含：生产计划、生产工单
-- ============================================================
SELECT '【6/6】生产模块初始化开始...' AS progress;

INSERT IGNORE INTO production_order (order_id, order_no, order_type, parent_order_id, sales_order_id, sales_order_no, product_id, product_code, product_name, product_spec, product_unit, routing_id, routing_code, planned_quantity, completed_quantity, remaining_quantity, plan_start_date, plan_end_date, actual_start_time, actual_end_time, order_status, approval_status, approver_id, approver_name, approval_time, approval_remark, priority, department_id, department_name, material_cost, labor_cost, total_cost, create_by, create_time, update_by, update_time, remark) VALUES
(1, 'PLAN-2024-001', 'PLAN', NULL, NULL, NULL, 1, 'FS-2024-001', '6键薄膜开关面板', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6}', 'pcs', 1, 'RT-FS001-V1', 500.0000, 0.0000, 500.0000, '2024-07-01', '2024-07-15', NULL, NULL, 4, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'MEDIUM', 104, '生产部', 4250.0000, 3150.0000, 7400.0000, 'system', NOW(), 'system', NOW(), '6键薄膜开关生产计划，数量500pcs'),
(2, 'PLAN-2024-002', 'PLAN', NULL, NULL, NULL, 2, 'FS-2024-002', '12键背光薄膜开关', '{"dimensions":{"width":120,"height":160,"unit":"mm"},"keyCount":12}', 'pcs', 2, 'RT-FS002-V1', 200.0000, 0.0000, 200.0000, '2024-07-10', '2024-07-30', NULL, NULL, 4, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'HIGH', 104, '生产部', 3300.0000, 1760.0000, 5060.0000, 'system', NOW(), 'system', NOW(), '12键背光薄膜开关生产计划，数量200pcs'),
(3, 'PLAN-2024-003', 'PLAN', NULL, NULL, NULL, 3, 'FS-2024-003', '4键触摸薄膜开关', '{"dimensions":{"width":60,"height":100,"unit":"mm"},"keyCount":4}', 'pcs', 3, 'RT-FS003-V1', 300.0000, 0.0000, 300.0000, '2024-08-01', '2024-08-15', NULL, NULL, 0, 'PENDING', NULL, NULL, NULL, NULL, 'LOW', 104, '生产部', 3600.0000, 1740.0000, 5340.0000, 'system', NOW(), 'system', NOW(), '4键触摸薄膜开关生产计划（待审批）'),
(4, 'WO-2024-001', 'WORK_ORDER', 1, NULL, NULL, 1, 'FS-2024-001', '6键薄膜开关面板', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6}', 'pcs', 1, 'RT-FS001-V1', 200.0000, 0.0000, 200.0000, '2024-07-01', '2024-07-08', NULL, NULL, 5, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'MEDIUM', 104, '生产部', 1700.0000, 1260.0000, 2960.0000, 'system', NOW(), 'system', NOW(), '6键薄膜开关第一批生产工单，数量200pcs'),
(5, 'WO-2024-002', 'WORK_ORDER', 1, NULL, NULL, 1, 'FS-2024-001', '6键薄膜开关面板', '{"dimensions":{"width":80,"height":120,"unit":"mm"},"keyCount":6}', 'pcs', 1, 'RT-FS001-V1', 300.0000, 0.0000, 300.0000, '2024-07-09', '2024-07-15', NULL, NULL, 5, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'MEDIUM', 104, '生产部', 2550.0000, 1890.0000, 4440.0000, 'system', NOW(), 'system', NOW(), '6键薄膜开关第二批生产工单，数量300pcs'),
(6, 'WO-2024-003', 'WORK_ORDER', 2, NULL, NULL, 2, 'FS-2024-002', '12键背光薄膜开关', '{"dimensions":{"width":120,"height":160,"unit":"mm"},"keyCount":12}', 'pcs', 2, 'RT-FS002-V1', 200.0000, 0.0000, 200.0000, '2024-07-10', '2024-07-30', NULL, NULL, 5, 'APPROVED', 1, '系统管理员', '2024-06-20 10:00:00', '批准生产', 'HIGH', 104, '生产部', 3300.0000, 1760.0000, 5060.0000, 'system', NOW(), 'system', NOW(), '12键背光薄膜开关生产工单，数量200pcs');
SELECT CONCAT('  生产订单数据: ', ROW_COUNT(), ' 条已插入') AS result;
SELECT '【6/6】生产模块初始化完成 ✅' AS progress;

-- ============================================================
-- 初始化完成
-- ============================================================
SELECT '========================================' AS summary;
SELECT '🎉 所有数据初始化完成！' AS summary;
SELECT '========================================' AS summary;
SELECT CONCAT('  部门: ', (SELECT COUNT(*) FROM sys_dept)) AS summary;
SELECT CONCAT('  角色: ', (SELECT COUNT(*) FROM sys_role)) AS summary;
SELECT CONCAT('  用户: ', (SELECT COUNT(*) FROM sys_user)) AS summary;
SELECT CONCAT('  菜单: ', (SELECT COUNT(*) FROM sys_menu)) AS summary;
SELECT CONCAT('  字典类型: ', (SELECT COUNT(*) FROM sys_dict)) AS summary;
SELECT CONCAT('  字典项: ', (SELECT COUNT(*) FROM sys_dict_item)) AS summary;
SELECT CONCAT('  仓库: ', (SELECT COUNT(*) FROM inventory_warehouse)) AS summary;
SELECT CONCAT('  库位: ', (SELECT COUNT(*) FROM inventory_storage_location)) AS summary;
SELECT CONCAT('  物料分类: ', (SELECT COUNT(*) FROM inventory_material_category)) AS summary;
SELECT CONCAT('  物料: ', (SELECT COUNT(*) FROM inventory_material)) AS summary;
SELECT CONCAT('  产品分类: ', (SELECT COUNT(*) FROM product_category)) AS summary;
SELECT CONCAT('  产品: ', (SELECT COUNT(*) FROM product)) AS summary;
SELECT CONCAT('  BOM: ', (SELECT COUNT(*) FROM product_bom)) AS summary;
SELECT CONCAT('  工艺路线: ', (SELECT COUNT(*) FROM product_routing)) AS summary;
SELECT CONCAT('  客户: ', (SELECT COUNT(*) FROM sales_customer)) AS summary;
SELECT CONCAT('  供应商: ', (SELECT COUNT(*) FROM purchase_supplier)) AS summary;
SELECT CONCAT('  生产订单: ', (SELECT COUNT(*) FROM production_order)) AS summary;
SELECT '========================================' AS summary;
SELECT '默认密码: admin123' AS summary;
SELECT '管理员账号: admin' AS summary;
SELECT '========================================' AS summary;
