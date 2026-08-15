-- =====================================================
-- 权限系统重建（方案 A：清超管以外，规范命名重建）
-- 2026-08-13 执行
-- 保留：admin 用户(1)、admin 角色(1)、JJX公司(1)
-- 清理：其余用户/角色/部门，重建规范命名
-- 角色 role_id 复用原编号（sys_role_menu 授权从备份 biz_backup_20260813_1854.sql 单独恢复）
-- 用户密码统一 123456（复用 admin BCrypt 哈希）
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. 清理 ====================
DELETE FROM sys_user_role WHERE user_id != 1;
DELETE FROM sys_role_menu WHERE role_id != 1;
DELETE FROM sys_user WHERE user_id != 1;
DELETE FROM sys_role WHERE role_id != 1;
DELETE FROM sys_dept WHERE dept_id != 1;

-- ==================== 2. 部门（规范命名） ====================
INSERT INTO sys_dept (dept_id, parent_id, dept_name, order_num, leader, status, del_flag, create_by, create_time, update_by, update_time) VALUES
(2,  1, '研发部',   1, NULL, '0', '0', 1, NOW(), 1, NOW()),
(3,  1, '市场部',   2, NULL, '0', '0', 1, NOW(), 1, NOW()),
(4,  1, '办公室',   3, NULL, '0', '0', 1, NOW(), 1, NOW()),
(15, 1, '采购部',   4, NULL, '0', '0', 1, NOW(), 1, NOW()),
(5,  1, '生产中心', 5, NULL, '0', '0', 1, NOW(), 1, NOW()),
(6,  5, '印刷车间', 1, NULL, '0', '0', 1, NOW(), 1, NOW()),
(7,  6, '印刷一组', 1, NULL, '0', '0', 1, NOW(), 1, NOW()),
(8,  6, '印刷二组', 2, NULL, '0', '0', 1, NOW(), 1, NOW()),
(9,  5, '冲型车间', 2, NULL, '0', '0', 1, NOW(), 1, NOW()),
(10, 9, '冲型一组', 1, NULL, '0', '0', 1, NOW(), 1, NOW()),
(11, 9, '冲型二组', 2, NULL, '0', '0', 1, NOW(), 1, NOW()),
(12, 5, '组装车间', 3, NULL, '0', '0', 1, NOW(), 1, NOW()),
(13, 12, '组装一组', 1, NULL, '0', '0', 1, NOW(), 1, NOW()),
(14, 12, '组装二组', 2, NULL, '0', '0', 1, NOW(), 1, NOW());

-- ==================== 3. 角色（复用原 role_id） ====================
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, update_by, update_time, remark, menu_check_strictly, dept_check_strictly) VALUES
(6,  'SYSTEM 全权限',         'system',                   6,  '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '系统管理员（办公室）', 1, 1),
(10, 'SALES 全权限',          'sales:all',                10, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '销售模块全权限', 1, 1),
(12, 'PRODUCT 全权限',        'product:all',              12, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '产品模块全权限', 1, 1),
(14, 'PRODUCT 业务操作',      'product:ops',              14, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '产品业务操作', 1, 1),
(15, 'PRODUCT 审核员',        'product:review',           15, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '产品审核', 1, 1),
(16, 'ENGINEERING 全权限',    'engineering:all',          16, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '工程模块全权限', 1, 1),
(17, 'ENGINEERING 业务操作',  'engineering:ops',          17, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '工程业务操作', 1, 1),
(18, 'ENGINEERING 审核员',    'engineering:review',       18, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '工程审核', 1, 1),
(20, 'SALES 业务操作',        'sales:ops',                20, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '销售业务操作', 1, 1),
(21, 'SALES 审核员',          'sales:review',             21, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '销售审核', 1, 1),
(22, 'INVENTORY 全权限',      'inventory:all',            22, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '库存模块全权限', 1, 1),
(23, 'INVENTORY 业务操作',    'inventory:ops',            23, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '库存业务操作', 1, 1),
(24, 'INVENTORY 审核员',      'inventory:review',         24, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '库存审核', 1, 1),
(25, 'PURCHASE 全权限',       'purchase:all',             25, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '采购模块全权限', 1, 1),
(26, 'PURCHASE 业务操作',     'purchase:ops',             26, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '采购业务操作', 1, 1),
(27, 'PURCHASE 审核员',       'purchase:review',          27, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '采购审核', 1, 1),
(28, 'PRODUCTION 全权限',     'production:all',           28, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '生产模块全权限', 1, 1),
(29, 'PRODUCTION 业务操作',   'production:ops',           29, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '生产业务操作', 1, 1),
(30, 'PRODUCTION 派工主管',   'production:dispatch_mgr',  30, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '车间主任：派工到班组', 1, 1),
(31, 'PRODUCTION 班组长',     'production:dispatch_leader', 31, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '班组长：本班组接单+定执行人', 1, 1),
(32, 'PRODUCTION 操作工',     'production:worker',        32, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '操作工（执行人第三级，纯岗位标识）', 1, 1);

-- ==================== 4. 用户（规范命名，密码 123456） ====================
INSERT INTO sys_user (user_name, nick_name, password, status, del_flag, create_by, create_time, update_by, update_time, dept_id, remark) VALUES
('office_ops',      '办公室管理员', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 4,  '办公室/系统管理'),
('sales_ops',       '销售业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 3,  '销售'),
('sales_review',    '销售审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 3,  '销售审核'),
('product_ops',     '产品业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '产品'),
('product_review',  '产品审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '产品审核'),
('engineer_ops',    '工程业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '工程'),
('engineer_review', '工程审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '工程审核'),
('buyer_ops',       '采购业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 15, '采购'),
('buyer_review',    '采购审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 15, '采购审核'),
('keeper_ops',      '仓管员',       '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 4,  '库存/仓管'),
('prod_mgr',        '生产中心主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 5,  '派工主管：派到班组级'),
('zhuren_print',    '印刷车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 6,  '派工主管'),
('zhuren_chong',    '冲型车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 9,  '派工主管'),
('zhuren_zu',       '组装车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 12, '派工主管'),
('bzz_print1',      '印刷一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 7,  '班组长'),
('bzz_print2',      '印刷二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 8,  '班组长'),
('bzz_chong1',      '冲型一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 10, '班组长'),
('bzz_chong2',      '冲型二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 11, '班组长'),
('bzz_zu1',         '组装一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 13, '班组长'),
('bzz_zu2',         '组装二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 14, '班组长'),
('worker_print1',   '印刷一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 7,  '操作工'),
('worker_print2',   '印刷二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 8,  '操作工'),
('worker_chong1',   '冲型一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 10, '操作工'),
('worker_chong2',   '冲型二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 11, '操作工'),
('worker_zu1',      '组装一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 13, '操作工'),
('worker_zu2',      '组装二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 14, '操作工');

-- ==================== 5. 用户-角色 ====================
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 6  FROM sys_user u WHERE u.user_name = 'office_ops';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 20 FROM sys_user u WHERE u.user_name = 'sales_ops';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 21 FROM sys_user u WHERE u.user_name = 'sales_review';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 14 FROM sys_user u WHERE u.user_name = 'product_ops';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 15 FROM sys_user u WHERE u.user_name = 'product_review';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 17 FROM sys_user u WHERE u.user_name = 'engineer_ops';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 18 FROM sys_user u WHERE u.user_name = 'engineer_review';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 26 FROM sys_user u WHERE u.user_name = 'buyer_ops';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 27 FROM sys_user u WHERE u.user_name = 'buyer_review';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 23 FROM sys_user u WHERE u.user_name = 'keeper_ops';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 28 FROM sys_user u WHERE u.user_name = 'prod_mgr';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 30 FROM sys_user u WHERE u.user_name IN ('prod_mgr', 'zhuren_print', 'zhuren_chong', 'zhuren_zu');
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 29 FROM sys_user u WHERE u.user_name LIKE 'zhuren_%' OR u.user_name LIKE 'bzz_%';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 31 FROM sys_user u WHERE u.user_name LIKE 'bzz_%';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 32 FROM sys_user u WHERE u.user_name LIKE 'worker_%';

-- ==================== 6. 部门负责人（leader 指向规范用户名） ====================
UPDATE sys_dept SET leader = 'engineer_ops' WHERE dept_id = 2;
UPDATE sys_dept SET leader = 'sales_ops'    WHERE dept_id = 3;
UPDATE sys_dept SET leader = 'office_ops'   WHERE dept_id = 4;
UPDATE sys_dept SET leader = 'buyer_ops'    WHERE dept_id = 15;
UPDATE sys_dept SET leader = 'prod_mgr'     WHERE dept_id = 5;
UPDATE sys_dept SET leader = 'zhuren_print' WHERE dept_id = 6;
UPDATE sys_dept SET leader = 'bzz_print1'   WHERE dept_id = 7;
UPDATE sys_dept SET leader = 'bzz_print2'   WHERE dept_id = 8;
UPDATE sys_dept SET leader = 'zhuren_chong' WHERE dept_id = 9;
UPDATE sys_dept SET leader = 'bzz_chong1'   WHERE dept_id = 10;
UPDATE sys_dept SET leader = 'bzz_chong2'   WHERE dept_id = 11;
UPDATE sys_dept SET leader = 'zhuren_zu'    WHERE dept_id = 12;
UPDATE sys_dept SET leader = 'bzz_zu1'      WHERE dept_id = 13;
UPDATE sys_dept SET leader = 'bzz_zu2'      WHERE dept_id = 14;

SET FOREIGN_KEY_CHECKS = 1;
