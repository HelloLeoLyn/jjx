-- ============================================================
-- 系统模块初始化数据
-- 执行顺序：第1个执行
-- 包含：部门、用户、角色、角色-用户关联、菜单、字典
-- ============================================================

-- ==================== 1. 部门数据 ====================
INSERT IGNORE INTO sys_dept (dept_id, parent_id, dept_name, order_num, leader, phone, email, status, del_flag, create_by, create_time, update_by, update_time) VALUES
(100, 0, 'JJX科技', 1, '系统管理员', '13800138000', 'admin@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(101, 100, '管理部', 1, '管理员', '13800138001', 'admin@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(102, 100, '销售部', 2, '销售经理', '13800138002', 'sales@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(103, 100, '采购部', 3, '采购经理', '13800138003', 'purchase@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(104, 100, '生产部', 4, '生产经理', '13800138004', 'production@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(105, 100, '品质部', 5, '品质经理', '13800138005', 'quality@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(106, 100, '仓储部', 6, '仓管经理', '13800138006', 'warehouse@jjx.com', '0', '0', 'system', NOW(), 'system', NOW()),
(107, 100, '技术部', 7, '技术经理', '13800138007', 'tech@jjx.com', '0', '0', 'system', NOW(), 'system', NOW());

-- ==================== 2. 角色数据 ====================
INSERT IGNORE INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, update_by, update_time, remark) VALUES
(1, '超级管理员', 'admin', 1, '1', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '超级管理员'),
(2, '销售员', 'sales', 2, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '销售员'),
(3, '采购员', 'purchase', 3, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '采购员'),
(4, '生产员', 'production', 4, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '生产员'),
(5, '质检员', 'quality', 5, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '质检员'),
(6, '仓管员', 'warehouse', 6, '2', 0, 0, '0', '0', 'system', NOW(), 'system', NOW(), '仓管员');

-- ==================== 3. 用户数据 ====================
-- 密码为 admin123 的 BCrypt 加密值
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phone, sex, avatar, password, salt, status, del_flag, create_by, create_time, update_by, update_time) VALUES
(1, 101, 'admin', '系统管理员', '00', 'admin@jjx.com', '13800138000', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(2, 102, 'sales', '销售员张三', '00', 'sales@jjx.com', '13800138002', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(3, 103, 'purchase', '采购员李四', '00', 'purchase@jjx.com', '13800138003', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(4, 104, 'production', '生产员王五', '00', 'production@jjx.com', '13800138004', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(5, 105, 'quality', '质检员赵六', '00', 'quality@jjx.com', '13800138005', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(6, 106, 'warehouse', '仓管员孙七', '00', 'warehouse@jjx.com', '13800138006', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW()),
(7, 107, 'tech', '技术员周八', '00', 'tech@jjx.com', '13800138007', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '', 0, '0', 'system', NOW(), 'system', NOW());

-- ==================== 4. 用户-角色关联 ====================
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4),
(5, 5),
(6, 6),
(7, 1);

-- ==================== 5. 菜单数据 ====================
-- 一级菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(1, '系统管理', 0, 1, 'system', '', '1', '0', 'M', '0', '0', '', 'system', 'system', NOW(), 'system', NOW(), '系统管理目录'),
(2, '销售管理', 0, 2, 'sales', '', '1', '0', 'M', '0', '0', '', 'sales-order', 'system', NOW(), 'system', NOW(), '销售管理目录'),
(3, '采购管理', 0, 3, 'purchase', '', '1', '0', 'M', '0', '0', '', 'purchase', 'system', NOW(), 'system', NOW(), '采购管理目录'),
(4, '生产管理', 0, 4, 'production', '', '1', '0', 'M', '0', '0', '', 'production', 'system', NOW(), 'system', NOW(), '生产管理目录'),
(5, '库存管理', 0, 5, 'inventory', '', '1', '0', 'M', '0', '0', '', 'inventory', 'system', NOW(), 'system', NOW(), '库存管理目录'),
(6, '产品管理', 0, 6, 'product', '', '1', '0', 'M', '0', '0', '', 'product', 'system', NOW(), 'system', NOW(), '产品管理目录'),
(7, '报表统计', 0, 7, 'report', '', '1', '0', 'M', '0', '0', '', 'report', 'system', NOW(), 'system', NOW(), '报表统计目录');

-- 系统管理子菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(101, '用户管理', 1, 1, 'user', 'system/user/index', '1', '0', 'C', '0', '0', 'system:user:list', 'user', 'system', NOW(), 'system', NOW(), '用户管理菜单'),
(102, '角色管理', 1, 2, 'role', 'system/role/index', '1', '0', 'C', '0', '0', 'system:role:list', 'role', 'system', NOW(), 'system', NOW(), '角色管理菜单'),
(103, '菜单管理', 1, 3, 'menu', 'system/menu/index', '1', '0', 'C', '0', '0', 'system:menu:list', 'menu', 'system', NOW(), 'system', NOW(), '菜单管理菜单'),
(104, '部门管理', 1, 4, 'dept', 'system/dept/index', '1', '0', 'C', '0', '0', 'system:dept:list', 'dept', 'system', NOW(), 'system', NOW(), '部门管理菜单'),
(105, '字典管理', 1, 5, 'dict', 'system/dict/index', '1', '0', 'C', '0', '0', 'system:dict:list', 'dict', 'system', NOW(), 'system', NOW(), '字典管理菜单'),
(106, '操作日志', 1, 6, 'operlog', 'system/operlog/index', '1', '0', 'C', '0', '0', 'system:operlog:list', 'log', 'system', NOW(), 'system', NOW(), '操作日志菜单'),
(107, '登录日志', 1, 7, 'loginlog', 'system/loginlog/index', '1', '0', 'C', '0', '0', 'system:loginlog:list', 'logininfor', 'system', NOW(), 'system', NOW(), '登录日志菜单'),
(108, '异常日志', 1, 8, 'errorlog', 'system/errorlog/index', '1', '0', 'C', '0', '0', 'system:errorlog:list', 'error', 'system', NOW(), 'system', NOW(), '异常日志菜单');

-- 销售管理子菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(201, '客户管理', 2, 1, 'customer', 'sales/customer/index', '1', '0', 'C', '0', '0', 'sales:customer:list', 'customer', 'system', NOW(), 'system', NOW(), '客户管理菜单'),
(202, '报价管理', 2, 2, 'quotation', 'sales/quotation/index', '1', '0', 'C', '0', '0', 'sales:quotation:list', 'quotation', 'system', NOW(), 'system', NOW(), '报价管理菜单'),
(203, '订单管理', 2, 3, 'order', 'sales/order/index', '1', '0', 'C', '0', '0', 'sales:order:list', 'order', 'system', NOW(), 'system', NOW(), '订单管理菜单');

-- 采购管理子菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(301, '供应商管理', 3, 1, 'supplier', 'purchase/supplier/index', '1', '0', 'C', '0', '0', 'purchase:supplier:list', 'supplier', 'system', NOW(), 'system', NOW(), '供应商管理菜单'),
(302, '采购订单', 3, 2, 'order', 'purchase/order/index', '1', '0', 'C', '0', '0', 'purchase:order:list', 'purchase-order', 'system', NOW(), 'system', NOW(), '采购订单菜单'),
(303, '采购入库', 3, 3, 'receipt', 'purchase/receipt/index', '1', '0', 'C', '0', '0', 'purchase:receipt:list', 'receipt', 'system', NOW(), 'system', NOW(), '采购入库菜单'),
(304, '采购付款', 3, 4, 'payment', 'purchase/payment/index', '1', '0', 'C', '0', '0', 'purchase:payment:list', 'payment', 'system', NOW(), 'system', NOW(), '采购付款菜单');

-- 生产管理子菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(401, '生产计划', 4, 1, 'plan', 'production/plan/index', '1', '0', 'C', '0', '0', 'production:plan:list', 'plan', 'system', NOW(), 'system', NOW(), '生产计划菜单'),
(402, '生产工单', 4, 2, 'work-order', 'production/work-order/index', '1', '0', 'C', '0', '0', 'production:workorder:list', 'work-order', 'system', NOW(), 'system', NOW(), '生产工单菜单'),
(403, '工序执行', 4, 3, 'operation', 'production/operation/index', '1', '0', 'C', '0', '0', 'production:operation:list', 'operation', 'system', NOW(), 'system', NOW(), '工序执行菜单');

-- 库存管理子菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(501, '仓库管理', 5, 1, 'warehouse', 'inventory/warehouse/index', '1', '0', 'C', '0', '0', 'inventory:warehouse:list', 'warehouse', 'system', NOW(), 'system', NOW(), '仓库管理菜单'),
(502, '库位管理', 5, 2, 'location', 'inventory/location/index', '1', '0', 'C', '0', '0', 'inventory:location:list', 'location', 'system', NOW(), 'system', NOW(), '库位管理菜单'),
(503, '物料管理', 5, 3, 'material', 'inventory/material/index', '1', '0', 'C', '0', '0', 'inventory:material:list', 'material', 'system', NOW(), 'system', NOW(), '物料管理菜单'),
(504, '物料分类', 5, 4, 'material-category', 'inventory/material-category/index', '1', '0', 'C', '0', '0', 'inventory:material:category:list', 'category', 'system', NOW(), 'system', NOW(), '物料分类菜单'),
(505, '入库管理', 5, 5, 'inbound', 'inventory/inbound/index', '1', '0', 'C', '0', '0', 'inventory:inbound:list', 'inbound', 'system', NOW(), 'system', NOW(), '入库管理菜单'),
(506, '出库管理', 5, 6, 'outbound', 'inventory/outbound/index', '1', '0', 'C', '0', '0', 'inventory:outbound:list', 'outbound', 'system', NOW(), 'system', NOW(), '出库管理菜单'),
(507, '库存查询', 5, 7, 'stock', 'inventory/stock/index', '1', '0', 'C', '0', '0', 'inventory:stock:list', 'stock', 'system', NOW(), 'system', NOW(), '库存查询菜单'),
(508, '库存盘点', 5, 8, 'stocktake', 'inventory/stocktake/index', '1', '0', 'C', '0', '0', 'inventory:stocktake:list', 'stocktake', 'system', NOW(), 'system', NOW(), '库存盘点菜单');

-- 产品管理子菜单
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(601, '产品分类', 6, 1, 'category', 'product/category/index', '1', '0', 'C', '0', '0', 'product:category:list', 'category', 'system', NOW(), 'system', NOW(), '产品分类菜单'),
(602, '产品管理', 6, 2, 'product', 'product/product/index', '1', '0', 'C', '0', '0', 'product:product:list', 'product', 'system', NOW(), 'system', NOW(), '产品管理菜单'),
(603, 'BOM管理', 6, 3, 'bom', 'product/bom/index', '1', '0', 'C', '0', '0', 'product:bom:list', 'bom', 'system', NOW(), 'system', NOW(), 'BOM管理菜单'),
(604, '工艺路线', 6, 4, 'routing', 'product/routing/index', '1', '0', 'C', '0', '0', 'product:routing:list', 'routing', 'system', NOW(), 'system', NOW(), '工艺路线菜单'),
(605, '标准工序', 6, 5, 'standard-process', 'product/standard-process/index', '1', '0', 'C', '0', '0', 'product:standard-process:list', 'process', 'system', NOW(), 'system', NOW(), '标准工序菜单');

-- ==================== 6. 字典数据 ====================
-- 字典类型
INSERT IGNORE INTO sys_dict (dict_id, dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, '通用状态', 'sys_normal_status', '0', 'system', NOW(), 'system', NOW(), '通用状态字典'),
(2, '性别', 'sys_user_sex', '0', 'system', NOW(), 'system', NOW(), '性别字典'),
(3, '是否', 'sys_yes_no', '0', 'system', NOW(), 'system', NOW(), '是否字典'),
(4, '物料类型', 'material_type', '0', 'system', NOW(), 'system', NOW(), '物料类型字典'),
(5, '订单状态', 'order_status', '0', 'system', NOW(), 'system', NOW(), '订单状态字典'),
(6, '工序类型', 'process_type', '0', 'system', NOW(), 'system', NOW(), '标准工序类型字典'),
(7, '工序类别', 'process_category', '0', 'system', NOW(), 'system', NOW(), '标准工序类别字典'),
(8, '仓库类型', 'warehouse_type', '0', 'system', NOW(), 'system', NOW(), '仓库类型字典'),
(9, '产品状态', 'product_status', '0', 'system', NOW(), 'system', NOW(), '产品状态字典'),
(10, '审核状态', 'approve_status', '0', 'system', NOW(), 'system', NOW(), '审核状态字典');

-- 字典数据
INSERT IGNORE INTO sys_dict_item (dict_item_id, dict_id, dict_code, dict_label, sort_order, status, create_by, create_time, update_by, update_time, remark) VALUES
-- 通用状态
(1, 1, '0', '正常', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(2, 1, '1', '停用', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 性别
(3, 2, '0', '男', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(4, 2, '1', '女', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(5, 2, '2', '未知', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 是否
(6, 3, '0', '否', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(7, 3, '1', '是', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 物料类型
(8, 4, 'R', '原材料', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(9, 4, 'S', '半成品', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(10, 4, 'F', '成品', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(11, 4, 'A', '辅助材料', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 工序类型
(12, 6, 'PRINTING', '印刷', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(13, 6, 'CUTTING', '模切', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(14, 6, 'LAMINATING', '贴合', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(15, 6, 'TESTING', '测试', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
(16, 6, 'PACKAGING', '包装', 5, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 工序类别
(17, 7, 'PREPARATION', '准备', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(18, 7, 'MAIN', '主要', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(19, 7, 'FINISHING', '后处理', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(20, 7, 'QUALITY', '质量', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 仓库类型
(21, 8, 'normal', '普通仓库', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(22, 8, 'quality', '质检仓库', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(23, 8, 'finished', '成品仓库', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
(24, 8, 'scrap', '废品仓库', 4, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 产品状态
(25, 9, '0', '开发中', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(26, 9, '1', '已发布', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(27, 9, '2', '已停产', 3, '0', 'system', NOW(), 'system', NOW(), NULL),
-- 审核状态
(28, 10, '0', '待审核', 1, '0', 'system', NOW(), 'system', NOW(), NULL),
(29, 10, '1', '已通过', 2, '0', 'system', NOW(), 'system', NOW(), NULL),
(30, 10, '2', '已驳回', 3, '0', 'system', NOW(), 'system', NOW(), NULL);

-- ============================================================
-- 数据验证
-- ============================================================
-- SELECT 'sys_dept' AS table_name, COUNT(*) AS count FROM sys_dept
-- UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
-- UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
-- UNION ALL SELECT 'sys_user_role', COUNT(*) FROM sys_user_role
-- UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu
-- UNION ALL SELECT 'sys_dict', COUNT(*) FROM sys_dict
-- UNION ALL SELECT 'sys_dict_item', COUNT(*) FROM sys_dict_item;
