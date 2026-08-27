-- =====================================================
-- P3 WorkReport + Approval
-- 03_work_report_approval.sql
--
-- 1. production_work_report 增加审批字段（一次审批：reviewer 统一，不拆 approved/rejected 两套）
-- 2. 状态收口：PENDING/APPROVED/REJECTED/CANCELLED（删除 SUBMITTED 语义）
--    —— 不做 SUBMITTED→PENDING 数据迁移：当前库 production_work_report 预期 0 行；
--       执行前请先执行下方核验 SELECT，若存在 SUBMITTED 行数 >0，停止并报告，不要自行迁移。
-- 3. 权限点：production:work-report:view / production:work-report:approve（最小 RBAC 门，P5 统一矩阵）
-- 4. 不新增 recalled_quantity / 不改 Task 数量模型 / 不建第二套完成事实源；
--    Task.pending/completed 均为投影（由 WorkReport 对账），不落库。
-- =====================================================

-- -----------------------------------------------------
-- 0. 数据核验（预期 0 行；>0 时停止并人工确认）
-- -----------------------------------------------------
-- SELECT report_status, COUNT(*) FROM production_work_report GROUP BY report_status;

-- -----------------------------------------------------
-- 1. 审批字段
-- -----------------------------------------------------
ALTER TABLE `production_work_report`
  ADD COLUMN `reviewer_id`   BIGINT       NULL DEFAULT NULL COMMENT '审批人ID（approve/reject 落库；一笔只审批一次）' AFTER `report_status`,
  ADD COLUMN `reviewer_name` VARCHAR(64)  NULL DEFAULT NULL COMMENT '审批人姓名快照（历史事实）' AFTER `reviewer_id`,
  ADD COLUMN `review_time`   DATETIME     NULL DEFAULT NULL COMMENT '审批时间' AFTER `reviewer_name`,
  ADD COLUMN `review_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '审批备注（驳回必填）' AFTER `review_time`;

-- 状态列注释收口（数据不变：当前 0 行，无需 UPDATE）
ALTER TABLE `production_work_report`
  MODIFY COLUMN `report_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审批/APPROVED已通过/REJECTED已驳回/CANCELLED已撤销';

-- -----------------------------------------------------
-- 2. 权限点（最小 RBAC 门；P5 完整权限矩阵统一收口）
--    view：我的报工/报工详情只读；approve：待我审批/审批通过/审批驳回
-- -----------------------------------------------------
INSERT INTO `sys_menu`
  (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
   `menu_type`, `visible`, `status`, `perms`, `icon`, `ancestors`, `route_name`, `requires_auth`,
   `redirect`, `sort`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '报工查询', 43, 12, '', NULL, NULL, '1', '0', 'F', '0', '0',
       'production:work-report:view', '#', NULL, NULL, '1', NULL, 0,
       'admin', NOW(), 'admin', NOW(), 'P3：我的报工/报工详情只读权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'production:work-report:view');

INSERT INTO `sys_menu`
  (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
   `menu_type`, `visible`, `status`, `perms`, `icon`, `ancestors`, `route_name`, `requires_auth`,
   `redirect`, `sort`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '报工审批', 43, 13, '', NULL, NULL, '1', '0', 'F', '0', '0',
       'production:work-report:approve', '#', NULL, NULL, '1', NULL, 0,
       'admin', NOW(), 'admin', NOW(), 'P3：待我审批/审批通过/审批驳回权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'production:work-report:approve');

-- 授权角色 1 超管 / 28 PRODUCTION 全权限 / 29 PRODUCTION 业务操作（与 add/cancel 一致）
-- 注意：真实审批角色（如组长）若需要“待我审批”，需人工在此追加 sys_role_menu 授权；
--       P5 完整权限矩阵统一收口。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 29) r
JOIN `sys_menu` m ON m.`perms` = 'production:work-report:view'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.role_id AND x.`menu_id` = m.`menu_id`
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 29) r
JOIN `sys_menu` m ON m.`perms` = 'production:work-report:approve'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.role_id AND x.`menu_id` = m.`menu_id`
);
