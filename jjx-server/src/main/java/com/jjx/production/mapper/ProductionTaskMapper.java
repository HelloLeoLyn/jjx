package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.production.domain.entity.ProductionTask;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskEventVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 生产任务 Mapper（统一任务责任树；P2 Task Flow 并发/条件更新）
 * <p>
 * 并发原则：
 * - 写动作统一锁顺序 parent → child（child.task_id 恒大于 parent，等于 task_id 升序）
 * - assign：父行 SELECT ... FOR UPDATE 串行化 → 锁内重算 remaining → INSERT children
 * - recall/return：父行 FOR UPDATE → 自身行 FOR UPDATE → 条件 UPDATE（version + affectedRows 校验）
 * - 所有条件 UPDATE 携带 version，影响行数 != 1 即并发冲突
 */
@Mapper
public interface ProductionTaskMapper extends BaseMapper<ProductionTask> {

    /** P6 第一层分页（keyword=工单号/工序名模糊；status 过滤；assigneeId 可空=不按执行人收窄） */
    @Select("<script>"
            + "SELECT t.* FROM production_task t "
            + "LEFT JOIN production_operation_execution e ON e.execution_id = t.execution_id "
            + "LEFT JOIN production_order o ON o.order_id = e.order_id "
            + "WHERE t.parent_task_id IS NULL AND t.status != 'CANCELLED' "
            + "<if test='status != null and status != \"\"'> AND t.status = #{status}</if> "
            + "<if test='keyword != null and keyword != \"\"'> "
            + "  AND (o.order_no LIKE CONCAT('%', #{keyword}, '%') "
            + "    OR e.process_name LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if> "
            + "<if test='assigneeId != null'> AND t.assignee_id = #{assigneeId}</if> "
            + "ORDER BY t.task_id DESC"
            + "</script>")
    Page<ProductionTask> selectFirstLevelPage(Page<ProductionTask> page, @Param("keyword") String keyword,
                                              @Param("status") String status, @Param("assigneeId") Long assigneeId);

    /** P6 流水：task_id = 当前任务 OR related_task_id = 当前任务（双向可见），按时间倒序 */
    @Select("SELECT ev.event_id, ev.task_id, ev.related_task_id, ev.action, "
            + "ev.operator_id, ev.operator_name, ev.from_assignee_id, ev.to_assignee_id, "
            + "COALESCE(NULLIF(fu.nick_name, ''), fu.user_name) AS from_assignee_name, "
            + "COALESCE(NULLIF(tu.nick_name, ''), tu.user_name) AS to_assignee_name, "
            + "ev.quantity, ev.before_task_quantity, ev.after_task_quantity, ev.remark, ev.create_time "
            + "FROM production_task_event ev "
            + "LEFT JOIN sys_user fu ON fu.user_id = ev.from_assignee_id "
            + "LEFT JOIN sys_user tu ON tu.user_id = ev.to_assignee_id "
            + "WHERE ev.task_id = #{taskId} OR ev.related_task_id = #{taskId} "
            + "ORDER BY ev.create_time DESC, ev.event_id DESC")
    List<TaskEventVO> selectTaskEvents(@Param("taskId") Long taskId);

    // ==================== P2 锁读 ====================

    @Select("SELECT * FROM production_task WHERE task_id = #{taskId} FOR UPDATE")
    ProductionTask selectByIdForUpdate(@Param("taskId") Long taskId);

    // ==================== P2 条件更新（version 修改检测） ====================

    /** 分配锁/修改检测：bump 父行 version（assign 不改父行业务字段，必须显式 bump 才能串行化） */
    @Update("UPDATE production_task SET version = version + 1 WHERE task_id = #{taskId} AND version = #{version}")
    int bumpVersion(@Param("taskId") Long taskId, @Param("version") Integer version);

    /** 扣减数量（recall/return 共用）；newStatus 由调用方计算（归 0 → CANCELLED） */
    @Update("UPDATE production_task SET task_quantity = task_quantity - #{quantity}, "
            + "status = #{newStatus}, version = version + 1 "
            + "WHERE task_id = #{taskId} AND version = #{version} AND status != 'CANCELLED'")
    int decreaseQuantity(@Param("taskId") Long taskId, @Param("quantity") BigDecimal quantity,
                         @Param("newStatus") String newStatus, @Param("version") Integer version);

    /** PENDING → ACTIVE：首次真正分配出 Child 后进入责任执行（P5：状态不再由 assignee_id 定义） */
    @Update("UPDATE production_task SET status = 'ACTIVE', version = version + 1 "
            + "WHERE task_id = #{taskId} AND status = 'PENDING' AND version = #{version}")
    int activateIfPending(@Param("taskId") Long taskId, @Param("version") Integer version);

    /**
     * 2026-09-05 Leo 定（审批链闭环）：父任务无负责人时自动补位为派单人。
     * 条件 assignee_id IS NULL 防并发抢注（调用方已 FOR UPDATE 锁行，双保险）。
     * 典型场景：一级任务(First Task)跨级直派——原来 T001.assignee 永远为空，
     * 子任务报工的 pending_reviewer 快照为空 → 无人能审（仅 production:all 兜底）。
     * 补位后：谁派单谁就是默认一级负责人，子任务报工由他审批，审批链不再悬空。
     */
    @Update("UPDATE production_task SET assignee_id = #{assigneeId}, update_by = #{updateBy}, "
            + "update_time = NOW() "
            + "WHERE task_id = #{taskId} AND assignee_id IS NULL")
    int claimUnassignedParent(@Param("taskId") Long taskId, @Param("assigneeId") Long assigneeId,
                              @Param("updateBy") String updateBy);

    /** 2026-09-05 分配留痕：task 行记录本次派单人（此前分配不改 create_by/update_by，历史无法追溯谁派的单） */
    @Update("UPDATE production_task SET update_by = #{updateBy}, update_time = NOW() "
            + "WHERE task_id = #{taskId}")
    int traceAssign(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);

    /** 人工确认完成：ACTIVE → COMPLETED（自底向上确认链；前置条件由 service 校验） */
    @Update("UPDATE production_task SET status = 'COMPLETED', version = version + 1 "
            + "WHERE task_id = #{taskId} AND status = 'ACTIVE' AND version = #{version}")
    int markCompleted(@Param("taskId") Long taskId, @Param("version") Integer version);

    /** 报工达标自动完成；状态条件保证重复审批或并发审批仅一次生效。 */
    @Update("UPDATE production_task SET status = #{completedStatus}, version = version + 1, "
            + "update_by = #{updateBy}, update_time = NOW() "
            + "WHERE task_id = #{taskId} AND status IN (#{pendingStatus}, #{activeStatus}) "
            + "AND task_quantity <= (SELECT COALESCE(SUM(qualified_quantity), 0) "
            + "FROM production_work_report WHERE task_id = #{taskId} AND report_status = #{approvedStatus})")
    int markCompletedByApprovedReports(@Param("taskId") Long taskId,
                                       @Param("pendingStatus") String pendingStatus,
                                       @Param("activeStatus") String activeStatus,
                                       @Param("completedStatus") String completedStatus,
                                       @Param("approvedStatus") String approvedStatus,
                                       @Param("updateBy") String updateBy);

    // ==================== P2 活动树投影（排除 CANCELLED） ====================

    /** Σ直接有效子节点 task_quantity（assignedQuantity 投影） */
    @Select("SELECT COALESCE(SUM(task_quantity), 0) FROM production_task "
            + "WHERE parent_task_id = #{taskId} AND status != 'CANCELLED'")
    BigDecimal sumEffectiveChildQuantity(@Param("taskId") Long taskId);

    /** 直接有效子节点数（排除 CANCELLED） */
    @Select("SELECT COUNT(*) FROM production_task "
            + "WHERE parent_task_id = #{taskId} AND status != 'CANCELLED'")
    Long countEffectiveChildren(@Param("taskId") Long taskId);

    /** 直接有效且未完成（非 COMPLETED）子节点数（COMPLETE 前置：所有有效直接 Child 必须已 COMPLETED） */
    @Select("SELECT COUNT(*) FROM production_task "
            + "WHERE parent_task_id = #{taskId} AND status != 'CANCELLED' AND status != 'COMPLETED'")
    Long countIncompleteChildren(@Param("taskId") Long taskId);

    /** 批量：parent_task_id → 未完成（非 COMPLETED）直接子节点数（allowedActions COMPLETE 投影，避免 N+1） */
    @Select("<script>"
            + "SELECT parent_task_id, COUNT(*) AS cnt FROM production_task "
            + "WHERE parent_task_id IN "
            + "<foreach collection='taskIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " AND status != 'CANCELLED' AND status != 'COMPLETED' "
            + "GROUP BY parent_task_id"
            + "</script>")
    List<Map<String, Object>> countIncompleteChildrenMap(@Param("taskIds") Collection<Long> taskIds);

    // ==================== 候选责任树（部门树 + 负责人关联；角色只作资格展示） ====================

    /**
     * 候选责任树扁平用户集：以 rootUserId 负责部门为根，递归全部后代部门中的正常用户。
     * - 部门负责人关系：sys_dept.leader_user_id（migration 04 回填）
     * - 返回含部门层级（parent_dept_id / leader_user_id），由 Resolver 组装人员树
     */
    @Select("WITH RECURSIVE desc_dept AS ("
            + "  SELECT dept_id, parent_id, leader_user_id FROM sys_dept WHERE leader_user_id = #{rootUserId} "
            + "  UNION ALL "
            + "  SELECT d.dept_id, d.parent_id, d.leader_user_id FROM sys_dept d "
            + "  JOIN desc_dept dd ON d.parent_id = dd.dept_id "
            + ") "
            + "SELECT u.user_id, u.user_name, u.nick_name, u.dept_id, d.dept_name, "
            + "       d.parent_id AS parent_dept_id, d.leader_user_id AS dept_leader_id, "
            + "       (SELECT r2.role_key FROM sys_user_role ur2 JOIN sys_role r2 ON r2.role_id = ur2.role_id "
            + "         WHERE ur2.user_id = u.user_id "
            + "           AND r2.role_key IN ('production:dispatch_mgr','production:dispatch_leader','production:worker') "
            + "         ORDER BY FIELD(r2.role_key,'production:dispatch_mgr','production:dispatch_leader','production:worker') "
            + "         LIMIT 1) AS role_key, "
            + "       (SELECT r3.role_name FROM sys_user_role ur3 JOIN sys_role r3 ON r3.role_id = ur3.role_id "
            + "         WHERE ur3.user_id = u.user_id "
            + "           AND r3.role_key IN ('production:dispatch_mgr','production:dispatch_leader','production:worker') "
            + "         ORDER BY FIELD(r3.role_key,'production:dispatch_mgr','production:dispatch_leader','production:worker') "
            + "         LIMIT 1) AS role_name "
            + "FROM desc_dept dd "
            + "JOIN sys_dept d ON d.dept_id = dd.dept_id "
            + "JOIN sys_user u ON u.dept_id = dd.dept_id "
            + "WHERE u.status = 0 AND (u.del_flag IS NULL OR u.del_flag = '0') "
            + "ORDER BY d.parent_id, d.order_num, u.user_id")
    List<TaskCandidateVO> selectAssigneeTreeUsers(@Param("rootUserId") Long rootUserId);

    /**
     * 批量「有下属」判断（canAssign 投影）：
     * 用户负责部门（含自身）的后代部门中是否存在非本人的正常用户。
     * - 组长与工人同部门：同部门其他 worker 算下属
     * - 无负责部门（如工人/admin）→ 不返回 → canAssign=false
     */
    @Select("<script>"
            + "WITH RECURSIVE desc_dept AS ("
            + "  SELECT dept_id, leader_user_id FROM sys_dept WHERE leader_user_id IN "
            + "<foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>"
            + "  UNION ALL "
            + "  SELECT d.dept_id, dd.leader_user_id FROM sys_dept d "
            + "  JOIN desc_dept dd ON d.parent_id = dd.dept_id "
            + ") "
            + "SELECT DISTINCT dd.leader_user_id AS user_id FROM desc_dept dd "
            + "JOIN sys_user u ON u.dept_id = dd.dept_id "
            + "WHERE u.user_id &lt;&gt; dd.leader_user_id "
            + "  AND u.status = 0 AND (u.del_flag IS NULL OR u.del_flag = '0')"
            + "</script>")
    List<Long> selectUsersHavingSubordinates(@Param("userIds") Collection<Long> userIds);
}
