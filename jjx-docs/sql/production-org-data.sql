-- =====================================================
-- 生产组织数据：部门树 + 岗位用户 + 派工角色（多级派工测试用）
-- 2026-08-13 生成，密码统一 123456（复用 admin BCrypt 哈希）
-- =====================================================

-- 1. 部门（生产中心 3 层：中心→车间→班组）
INSERT INTO sys_dept (dept_id, parent_id, dept_name, order_num, leader, status, del_flag, create_by, create_time, update_by, update_time) VALUES
(5,  1, '生产中心', 1, 'prod_mgr', '0', '0', 1, NOW(), 1, NOW()),
(6,  5, '印刷车间', 1, 'zhuren_print', '0', '0', 1, NOW(), 1, NOW()),
(7,  6, '印刷一组', 1, 'bzz_print1', '0', '0', 1, NOW(), 1, NOW()),
(8,  6, '印刷二组', 2, 'bzz_print2', '0', '0', 1, NOW(), 1, NOW()),
(9,  5, '冲型车间', 2, 'zhuren_chong', '0', '0', 1, NOW(), 1, NOW()),
(10, 9, '冲型一组', 1, 'bzz_chong1', '0', '0', 1, NOW(), 1, NOW()),
(11, 9, '冲型二组', 2, 'bzz_chong2', '0', '0', 1, NOW(), 1, NOW()),
(12, 5, '组装车间', 3, 'zhuren_zu', '0', '0', 1, NOW(), 1, NOW()),
(13, 12, '组装一组', 1, 'bzz_zu1', '0', '0', 1, NOW(), 1, NOW()),
(14, 12, '组装二组', 2, 'bzz_zu2', '0', '0', 1, NOW(), 1, NOW());

-- 2. 用户（16 个：1 中心主任 + 3 车间主任 + 6 班组长 + 6 工人，密码 123456）
INSERT INTO sys_user (user_name, nick_name, password, status, del_flag, create_by, create_time, update_by, update_time, dept_id, remark) VALUES
('prod_mgr',     '生产中心主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 5,  '派工主管：派到班组级'),
('zhuren_print', '印刷车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 6,  '派工主管：派到班组级'),
('zhuren_chong', '冲型车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 9,  '派工主管：派到班组级'),
('zhuren_zu',    '组装车间主任', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 12, '派工主管：派到班组级'),
('bzz_print1',   '印刷一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 7,  '班组长：接单+定执行人'),
('bzz_print2',   '印刷二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 8,  '班组长：接单+定执行人'),
('bzz_chong1',   '冲型一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 10, '班组长：接单+定执行人'),
('bzz_chong2',   '冲型二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 11, '班组长：接单+定执行人'),
('bzz_zu1',      '组装一组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 13, '班组长：接单+定执行人'),
('bzz_zu2',      '组装二组组长', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 14, '班组长：接单+定执行人'),
('worker_print1','印刷一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 7,  '执行人'),
('worker_print2','印刷二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 8,  '执行人'),
('worker_chong1','冲型一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 10, '执行人'),
('worker_chong2','冲型二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 11, '执行人'),
('worker_zu1',   '组装一组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 13, '执行人'),
('worker_zu2',   '组装二组工人', '$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi', 0, '0', 'admin', NOW(), 'admin', NOW(), 14, '执行人');

-- 3. 角色（2 个派工岗位角色）
INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time, update_by, update_time, remark, menu_check_strictly, dept_check_strictly) VALUES
('PRODUCTION 派工主管', 'production:dispatch_mgr',    30, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '车间主任：派工到班组级', 1, 1),
('PRODUCTION 班组长',   'production:dispatch_leader', 31, '1', '0', '0', 'admin', NOW(), 'admin', NOW(), '班组长：本班组接单+定执行人', 1, 1);

-- 4. 角色-菜单（派工主管：查询+指派；班组长：查询+指派+开始/完成）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(30, 261), (30, 262),
(31, 261), (31, 262), (31, 263);

-- 5. 用户-角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 30 FROM sys_user u WHERE u.user_name IN ('prod_mgr', 'zhuren_print', 'zhuren_chong', 'zhuren_zu');
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 31 FROM sys_user u WHERE u.user_name IN ('bzz_print1', 'bzz_print2', 'bzz_chong1', 'bzz_chong2', 'bzz_zu1', 'bzz_zu2');
