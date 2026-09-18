-- ============================================================================
-- 141_grant_workreport_approve.sql
-- 任务码：dev-20260918-013
--
-- 目的：把「报工审批」权限点（菜单 293 production:work-report:approve）下放给
--       车间两级主管角色，让班组长/车间主任能审批「下属任务」产生的报工。
--
-- 背景：工序执行页「待我审批 / 通过 / 驳回」全部由 production:work-report:approve 控制
--       （前端 v-hasPermi + 后端 WorkReportController @SaCheckPermission）。
--       该权限点此前只授给 超管(1) / PRODUCTION 全权限(28) / PRODUCTION 业务操作(29)，
--       而 29 当前无任何用户绑定；30 派工主管、31 班组长、32 操作工 均未授权，
--       导致林浩(146)/温丽云(147) 等车间主管看不到审批入口、直接调接口也 403。
--
-- 分配规则（用户 2026-09-18 确认「A」）：
--   仅给 30 production:dispatch_mgr（派工主管/车间主任）与 31 production:dispatch_leader（班组长）。
--   不给 32 production:worker（操作工不应审批自己报的工）；不动 28/29；不动其他权限点。
--
-- 审批关系约束（本迁移只放行权限点，范围仍由后端把关，无需前端改动）：
--   - 列表：GET /production/work-report/pending-approval 非全局范围只返回
--     production_task.parent.assignee_id = 当前用户 的子任务报工；
--   - 动作：WorkReportActionServiceImpl.checkApprover 非全局范围要求
--     operatorId == 报工提交时快照的 pending_reviewer_id（= 父任务执行人）。
--   因此授权后主管只能审批「自己名下父任务」的报工，不会变成无差别全批；
--   首道任务（无父任务）仍只有 production:all 能批。
--
-- 幂等：INSERT ... WHERE NOT EXISTS，可重复执行。
-- 回滚：DELETE FROM sys_role_menu WHERE menu_id = 293 AND role_id IN (30, 31);
-- ============================================================================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 293
  FROM sys_role r
 WHERE r.role_id IN (30, 31)
   AND NOT EXISTS (
        SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 293
   );

-- ============================================================================
-- 执行后自检（应返回 5 行：1 超管 / 28 全权限 / 29 业务操作 / 30 派工主管 / 31 班组长）：
--   SELECT rm.role_id, r.role_name FROM sys_role_menu rm
--     JOIN sys_role r ON r.role_id = rm.role_id
--    WHERE rm.menu_id = 293 ORDER BY rm.role_id;
-- ============================================================================
