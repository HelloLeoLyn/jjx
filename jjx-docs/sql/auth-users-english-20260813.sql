-- =====================================================
-- 用户统一为英文岗位名（2026-08-13 用户确认）
-- 替换方案A的拼音/英文混合用户名，全部英文小写岗位名
-- 昵称=中文岗位；角色管权限；部门 leader 同步
-- 密码统一 123456（复用 admin BCrypt 哈希）
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清非 admin 用户及绑定
DELETE FROM sys_user_role WHERE user_id != 1;
DELETE FROM sys_user WHERE user_id != 1;

-- 2. 用户（26 个英文岗位名）
INSERT INTO sys_user (user_name, nick_name, password, status, del_flag, create_by, create_time, update_by, update_time, dept_id, remark) VALUES
('office_mgr',       '办公室管理员', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 4,  '办公室/系统管理'),
('sales_clerk',      '销售业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 3,  '销售'),
('sales_reviewer',   '销售审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 3,  '销售审核'),
('product_clerk',    '产品业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '产品'),
('product_reviewer', '产品审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '产品审核'),
('engineer_clerk',   '工程业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '工程'),
('engineer_reviewer','工程审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 2,  '工程审核'),
('buyer_clerk',      '采购业务员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 15, '采购'),
('buyer_reviewer',   '采购审核员',   '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 15, '采购审核'),
('warehouse_keeper', '仓管员',       '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 4,  '库存/仓管'),
('prod_manager',     '生产中心主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 5,  '派工主管：派到班组级'),
('print_mgr',        '印刷车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 6,  '派工主管'),
('punch_mgr',        '冲型车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 9,  '派工主管'),
('assembly_mgr',     '组装车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 12, '派工主管'),
('print_leader1',    '印刷一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 7,  '班组长'),
('print_leader2',    '印刷二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 8,  '班组长'),
('punch_leader1',    '冲型一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 10, '班组长'),
('punch_leader2',    '冲型二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 11, '班组长'),
('assembly_leader1', '组装一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 13, '班组长'),
('assembly_leader2', '组装二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 14, '班组长'),
('print_op1',        '印刷一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 7,  '操作工'),
('print_op2',        '印刷二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 8,  '操作工'),
('punch_op1',        '冲型一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 10, '操作工'),
('punch_op2',        '冲型二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 11, '操作工'),
('assembly_op1',     '组装一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 13, '操作工'),
('assembly_op2',     '组装二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 14, '操作工');

-- 3. 用户-角色绑定
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 6  FROM sys_user u WHERE u.user_name = 'office_mgr';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 20 FROM sys_user u WHERE u.user_name = 'sales_clerk';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 21 FROM sys_user u WHERE u.user_name = 'sales_reviewer';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 14 FROM sys_user u WHERE u.user_name = 'product_clerk';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 15 FROM sys_user u WHERE u.user_name = 'product_reviewer';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 17 FROM sys_user u WHERE u.user_name = 'engineer_clerk';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 18 FROM sys_user u WHERE u.user_name = 'engineer_reviewer';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 26 FROM sys_user u WHERE u.user_name = 'buyer_clerk';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 27 FROM sys_user u WHERE u.user_name = 'buyer_reviewer';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 23 FROM sys_user u WHERE u.user_name = 'warehouse_keeper';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 28 FROM sys_user u WHERE u.user_name = 'prod_manager';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 30 FROM sys_user u WHERE u.user_name IN ('prod_manager', 'print_mgr', 'punch_mgr', 'assembly_mgr');
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 29 FROM sys_user u WHERE u.user_name LIKE '%_mgr' OR u.user_name LIKE '%_leader%';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 31 FROM sys_user u WHERE u.user_name LIKE '%_leader%';
INSERT INTO sys_user_role (user_id, role_id) SELECT u.user_id, 32 FROM sys_user u WHERE u.user_name LIKE '%_op%';

-- 4. 部门 leader 同步（英文岗位名）
UPDATE sys_dept SET leader = 'engineer_clerk' WHERE dept_id = 2;
UPDATE sys_dept SET leader = 'sales_clerk'    WHERE dept_id = 3;
UPDATE sys_dept SET leader = 'office_mgr'     WHERE dept_id = 4;
UPDATE sys_dept SET leader = 'buyer_clerk'    WHERE dept_id = 15;
UPDATE sys_dept SET leader = 'prod_manager'   WHERE dept_id = 5;
UPDATE sys_dept SET leader = 'print_mgr'      WHERE dept_id = 6;
UPDATE sys_dept SET leader = 'print_leader1'  WHERE dept_id = 7;
UPDATE sys_dept SET leader = 'print_leader2'  WHERE dept_id = 8;
UPDATE sys_dept SET leader = 'punch_mgr'      WHERE dept_id = 9;
UPDATE sys_dept SET leader = 'punch_leader1'  WHERE dept_id = 10;
UPDATE sys_dept SET leader = 'punch_leader2'  WHERE dept_id = 11;
UPDATE sys_dept SET leader = 'assembly_mgr'   WHERE dept_id = 12;
UPDATE sys_dept SET leader = 'assembly_leader1' WHERE dept_id = 13;
UPDATE sys_dept SET leader = 'assembly_leader2' WHERE dept_id = 14;

SET FOREIGN_KEY_CHECKS = 1;
