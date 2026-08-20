-- ============================================================
-- WP-E 测试队员用户数据（2026-08-20）
-- 说明：为生产域多人分配（Assignment）测试补充队员账号
--       每组补 2 名队员，与现有 op1/op2 组成 3 人/组
-- 密码：统一 123456（BCrypt 哈希）
-- 角色：32 PRODUCTION 操作工
-- ============================================================

INSERT INTO sys_user (user_name, nick_name, user_type, password, salt, status, del_flag, create_by, create_time, update_by, update_time, dept_id, remark) VALUES
('print_op1b','印刷一组工人B','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),7,'WP-E 测试队员'),
('print_op1c','印刷一组工人C','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),7,'WP-E 测试队员'),
('print_op2b','印刷二组工人B','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),8,'WP-E 测试队员'),
('print_op2c','印刷二组工人C','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),8,'WP-E 测试队员'),
('punch_op1b','冲型一组工人B','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),10,'WP-E 测试队员'),
('punch_op1c','冲型一组工人C','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),10,'WP-E 测试队员'),
('punch_op2b','冲型二组工人B','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),11,'WP-E 测试队员'),
('punch_op2c','冲型二组工人C','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),11,'WP-E 测试队员'),
('assembly_op1b','组装一组工人B','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),13,'WP-E 测试队员'),
('assembly_op1c','组装一组工人C','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),13,'WP-E 测试队员'),
('assembly_op2b','组装二组工人B','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),14,'WP-E 测试队员'),
('assembly_op2c','组装二组工人C','00','$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','admin',NOW(),'admin',NOW(),14,'WP-E 测试队员');

-- 绑定操作工角色 32
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 32 FROM sys_user u WHERE u.user_name IN ('print_op1b','print_op1c','print_op2b','print_op2c','punch_op1b','punch_op1c','punch_op2b','punch_op2c','assembly_op1b','assembly_op1c','assembly_op2b','assembly_op2c');
