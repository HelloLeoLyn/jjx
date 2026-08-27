-- =====================================================
-- P4.5 Candidate Assignee Tree 组织数据地基
-- 04_assignee_tree_org.sql
--
-- 背景：当前 sys_dept.leader 只是负责人 user_name 字符串，
--       无法可靠表达「部门负责人 → 该部门全部后代部门人员 = 下属」。
-- 本次：新增 sys_dept.leader_user_id（部门负责人用户ID），
--       并按现有 leader(user_name) 字符串回填生产部门。
-- 原则：候选责任树 = 部门树 + 负责人关联；角色只作资格展示，不再用 role_key 猜层级。
-- =====================================================

ALTER TABLE `sys_dept`
  ADD COLUMN `leader_user_id` BIGINT NULL DEFAULT NULL COMMENT '部门负责人用户ID（候选责任树：负责人→该部门全部后代部门人员=下属）' AFTER `leader`;

-- 回填：leader 字符串与 sys_user.user_name 对齐的部门（当前 9 个生产部门全部命中；
-- 非生产部门 leader 为中文姓名或不填，保持 NULL，不参与生产责任树）
UPDATE `sys_dept` d
JOIN `sys_user` u ON u.user_name = d.leader
SET d.leader_user_id = u.user_id
WHERE d.leader IS NOT NULL AND d.leader <> '';

CREATE INDEX `idx_dept_leader_user` ON `sys_dept` (`leader_user_id`);
