package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.TaskAssignDTO;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.dto.TaskCompleteDTO;
import com.jjx.production.domain.dto.TaskRecallDTO;
import com.jjx.production.domain.dto.TaskReturnDTO;
import com.jjx.production.domain.dto.TaskTreeQueryDTO;
import com.jjx.production.domain.dto.MyProductionExecutionQueryDTO;
import com.jjx.production.domain.entity.ProductionTask;
import com.jjx.production.domain.entity.ProductionTaskEvent;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskCompletionDetailVO;
import com.jjx.production.domain.vo.TaskEventVO;
import com.jjx.production.domain.vo.TaskTreeRowVO;
import com.jjx.production.domain.vo.MyProductionExecutionVO;
import com.jjx.production.domain.vo.ChildProcessingDetailVO;
import com.jjx.production.enums.ProductionTaskStatus;
import com.jjx.production.mapper.ProductionTaskEventMapper;
import com.jjx.production.mapper.ProductionTaskMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionTaskAssigneeResolver;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产任务服务实现（统一任务责任树；P1 Foundation + P2 Task Flow）
 * <p>
 * 查询范围（P1 最小规则）：
 * - 生产全局角色（*:*:* / admin / production:all）→ 查全部第一层 Task
 * - 普通用户 → 只查 assignee_id = 当前用户 的第一层 Task
 * children / detail 的完整数据范围在 P5 与 RBAC 一起收口。
 * <p>
 * 数量口径（P4 Completion Projection & Reconciliation）：
 * - completedQuantity  = 当前 Task 整个有效 subtree 的 APPROVED WorkReport 合计（展示归集）
 * - pendingQuantity    = 当前 Task 整个有效 subtree 的 PENDING WorkReport 合计（展示归集）
 * - assignedQuantity   = 展示值：下游仍未 completed/pending 的有效责任量
 *                       = childAssigned - (subtreeCompleted - ownCompleted) - (subtreePending - ownPending)
 *                       （仅 UI 展示；写 gate 一律使用 childAssigned + ownRemaining，禁止使用该展示值）
 * - remainingQuantity  = gate 口径：taskQuantity - childAssigned - ownPending - ownCompleted（下限 0）
 * 树级展示不变式：taskQuantity = completed + pending + assigned + remaining（代数恒成立）
 * 写侧 gate（assign/recall/return/complete/submit）保持 P2/P3 口径不变。
 * <p>
 * 并发（P2）：
 * - 锁顺序统一 parent → child（child.task_id 恒大于 parent，等于 task_id 升序）
 * - assign：父行 SELECT ... FOR UPDATE 串行化 → 锁内重算 remaining → INSERT children
 * - recall/return：父行 FOR UPDATE → 自身行 FOR UPDATE → 条件 UPDATE（version + affectedRows）
 * - TaskEvent 与 Task 修改同一事务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionTaskServiceImpl implements ProductionTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String REPORT_STATUS_PENDING = "PENDING";
    private static final String REPORT_STATUS_APPROVED = "APPROVED";

    private static final String ACTION_ASSIGN = "ASSIGN";
    private static final String ACTION_RECALL = "RECALL";
    private static final String ACTION_RETURN = "RETURN";
    private static final String ACTION_COMPLETE = "COMPLETE";
    private static final String ROLE_PRODUCTION_MANAGER = "production:all";

    private final ProductionTaskMapper productionTaskMapper;
    private final ProductionTaskEventMapper productionTaskEventMapper;
    private final ProductionTaskAssigneeResolver assigneeResolver;
    private final ProductionWorkReportMapper productionWorkReportMapper;
    private final ProductionOperationExecutionMapper productionOperationExecutionMapper;
    private final JdbcTemplate jdbcTemplate;

    // ==================== P1 Foundation ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFirstTask(Long executionId, BigDecimal inputQuantity) {
        if (executionId == null) {
            throw new BusinessException("executionId 不能为空");
        }
        String taskNo = nextTaskNo(executionId, true);
        Long existing = findFirstTask(executionId);
        if (existing != null) return existing;
        ProductionTask task = new ProductionTask();
        task.setTaskNo(taskNo);
        task.setExecutionId(executionId);
        task.setParentTaskId(null);
        task.setAssigneeId(null);
        task.setTaskQuantity(inputQuantity == null ? BigDecimal.ZERO : inputQuantity);
        task.setStatus(STATUS_PENDING);
        task.setVersion(0);
        try {
            productionTaskMapper.insert(task);
            return task.getTaskId();
        } catch (DuplicateKeyException e) {
            // 唯一约束 uk_exec_first 兜底：并发/重复创建时幂等返回既有 First Task
            log.warn("First Task 已存在（并发或重复创建），executionId={}", executionId);
            return findFirstTask(executionId);
        }
    }

    @Override
    public Page<TaskTreeRowVO> pageAccessibleTasks(TaskTreeQueryDTO queryDTO) {
        int pageNum = queryDTO == null || queryDTO.getPageNum() == null ? 1 : queryDTO.getPageNum();
        int pageSize = queryDTO == null || queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();
        
        LambdaQueryWrapper<ProductionTask> wrapper = Wrappers.lambdaQuery();

        if (SecurityUtils.isGlobalProductionScope()) {
            wrapper.isNull(ProductionTask::getParentTaskId);
        } else {
            wrapper.eq(ProductionTask::getAssigneeId, SecurityUtils.getUserId());
        }
        if (queryDTO != null && org.apache.commons.lang3.StringUtils.isNotBlank(queryDTO.getStatus())) {
            wrapper.eq(ProductionTask::getStatus, queryDTO.getStatus().trim().toUpperCase());
        }
        if (queryDTO != null && org.apache.commons.lang3.StringUtils.isNotBlank(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.apply("EXISTS (SELECT 1 FROM production_operation_execution e "
                    + "LEFT JOIN production_order o ON o.order_id = e.order_id "
                    + "WHERE e.execution_id = production_task.execution_id "
                    + "AND (o.order_no LIKE CONCAT('%',{0},'%') "
                    + "OR e.process_name LIKE CONCAT('%',{0},'%')))", keyword);
        }

        Page<ProductionTask> page = productionTaskMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        List<TaskTreeRowVO> rows = project(page.getRecords(), null);

        Page<TaskTreeRowVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(rows);
        return voPage;
    }

    @Override
    public Page<MyProductionExecutionVO> pageMyProductionExecutions(MyProductionExecutionQueryDTO queryDTO) {
        MyProductionExecutionQueryDTO q = queryDTO == null ? new MyProductionExecutionQueryDTO() : queryDTO;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);
        Long userId = SecurityUtils.getUserId();

        StringBuilder where = new StringBuilder(" WHERE t.assignee_id=? AND t.status!='CANCELLED'");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (q.getOrderNo() != null && !q.getOrderNo().isBlank()) {
            where.append(" AND o.order_no LIKE ?");
            args.add("%" + q.getOrderNo().trim() + "%");
        }
        if (q.getProcessName() != null && !q.getProcessName().isBlank()) {
            where.append(" AND COALESCE(NULLIF(e.process_name,''),p.process_name) LIKE ?");
            args.add("%" + q.getProcessName().trim() + "%");
        }
        if (q.getExecutionStatus() != null && !q.getExecutionStatus().isBlank()) {
            where.append(" AND e.execution_status=?");
            args.add(Integer.valueOf(q.getExecutionStatus()));
        }
        if (q.getEquipmentId() != null) {
            where.append(" AND e.equipment_id=?");
            args.add(q.getEquipmentId());
        }
        String joins = " FROM production_task t"
                + " JOIN production_operation_execution e ON e.execution_id=t.execution_id"
                + " LEFT JOIN production_order o ON o.order_id=e.order_id"
                + " LEFT JOIN engineering_standard_process p ON p.process_id=e.process_id";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT t.execution_id)" + joins + where,
                Long.class, args.toArray());
        Page<MyProductionExecutionVO> result = new Page<>(pageNum, pageSize, total == null ? 0 : total);
        if (total == null || total == 0) return result;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((pageNum - 1) * pageSize);
        pageArgs.add(pageSize);
        List<Long> executionIds = jdbcTemplate.query(
                "SELECT t.execution_id" + joins + where
                        + " GROUP BY t.execution_id ORDER BY MAX(t.task_id) DESC LIMIT ?,?",
                (rs, rowNum) -> rs.getLong("execution_id"), pageArgs.toArray());
        if (executionIds.isEmpty()) return result;

        List<ProductionTask> myTasks = productionTaskMapper.selectList(Wrappers.<ProductionTask>lambdaQuery()
                .eq(ProductionTask::getAssigneeId, userId)
                .in(ProductionTask::getExecutionId, executionIds)
                .ne(ProductionTask::getStatus, STATUS_CANCELLED));
        Map<Long, TaskTreeRowVO> projectedByTask = project(myTasks, null).stream()
                .collect(Collectors.toMap(TaskTreeRowVO::getTaskId, row -> row));
        List<Long> myTaskIds = myTasks.stream().map(ProductionTask::getTaskId).toList();
        List<ProductionTask> children = myTaskIds.isEmpty() ? List.of()
                : productionTaskMapper.selectList(Wrappers.<ProductionTask>lambdaQuery()
                .in(ProductionTask::getParentTaskId, myTaskIds)
                .ne(ProductionTask::getStatus, STATUS_CANCELLED));
        Map<Long, BigDecimal[]> childSubtree = subtreeAggregates(children.stream()
                .map(ProductionTask::getTaskId).toList());
        Map<Long, BigDecimal> ownApproved = reportTotals(myTaskIds, REPORT_STATUS_APPROVED);
        Map<Long, BigDecimal> ownPending = reportTotals(myTaskIds, REPORT_STATUS_PENDING);
        Map<Long, BigDecimal> childPending = reportTotals(children.stream()
                .map(ProductionTask::getTaskId).toList(), REPORT_STATUS_PENDING);

        Map<Long, MyProductionExecutionVO> byExecution = new HashMap<>();
        String ids = executionIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.query("SELECT e.execution_id,e.order_id,o.order_no,e.process_id,"
                        + " COALESCE(NULLIF(e.process_name,''),p.process_name) process_name,e.process_order,"
                        + " e.execution_status,e.actual_start_time,e.equipment_id,e.equipment_code,e.equipment_name,e.input_quantity"
                        + " FROM production_operation_execution e"
                        + " LEFT JOIN production_order o ON o.order_id=e.order_id"
                        + " LEFT JOIN engineering_standard_process p ON p.process_id=e.process_id"
                        + " WHERE e.execution_id IN (" + ids + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    MyProductionExecutionVO vo = new MyProductionExecutionVO();
                    vo.setExecutionId(rs.getLong("execution_id"));
                    vo.setOrderId(rs.getLong("order_id"));
                    vo.setOrderNo(rs.getString("order_no"));
                    vo.setProcessId(rs.getObject("process_id") == null ? null : rs.getLong("process_id"));
                    vo.setProcessName(rs.getString("process_name"));
                    vo.setProcessOrder(rs.getObject("process_order") == null ? null : rs.getInt("process_order"));
                    vo.setExecutionStatus(rs.getInt("execution_status"));
                    vo.setActualStartTime(rs.getTimestamp("actual_start_time") == null
                            ? null : rs.getTimestamp("actual_start_time").toLocalDateTime());
                    vo.setEquipmentId(rs.getObject("equipment_id") == null ? null : rs.getLong("equipment_id"));
                    vo.setEquipmentCode(rs.getString("equipment_code"));
                    vo.setEquipmentName(rs.getString("equipment_name"));
                    vo.setTaskCount(0);
                    vo.setPlannedQuantity(zero(rs.getBigDecimal("input_quantity")));
                    vo.setMyResponsibilityQuantity(BigDecimal.ZERO);
                    vo.setMyCompletedQuantity(BigDecimal.ZERO);
                    vo.setMyPendingReviewQuantity(BigDecimal.ZERO);
                    vo.setMyProcessableQuantity(BigDecimal.ZERO);
                    vo.setChildCompletedQuantity(BigDecimal.ZERO);
                    vo.setChildProcessingQuantity(BigDecimal.ZERO);
                    vo.setPendingMyApprovalQuantity(BigDecimal.ZERO);
                    byExecution.put(vo.getExecutionId(), vo);
                });
        Map<Long, ProductionTask> myTaskById = myTasks.stream()
                .collect(Collectors.toMap(ProductionTask::getTaskId, task -> task));
        for (ProductionTask task : myTasks) {
            MyProductionExecutionVO vo = byExecution.get(task.getExecutionId());
            if (vo == null) continue;
            int taskCount = vo.getTaskCount() + 1;
            vo.setTaskCount(taskCount);
            vo.setTaskNo(taskCount == 1 ? task.getTaskNo() : null);
            vo.setMyResponsibilityQuantity(vo.getMyResponsibilityQuantity().add(zero(task.getTaskQuantity())));
            vo.setMyCompletedQuantity(vo.getMyCompletedQuantity()
                    .add(ownApproved.getOrDefault(task.getTaskId(), BigDecimal.ZERO)));
            vo.setMyPendingReviewQuantity(vo.getMyPendingReviewQuantity()
                    .add(ownPending.getOrDefault(task.getTaskId(), BigDecimal.ZERO)));
            TaskTreeRowVO row = projectedByTask.get(task.getTaskId());
            if (row != null) {
                vo.setMyProcessableQuantity(vo.getMyProcessableQuantity().add(zero(row.getRemainingQuantity())));
            }
        }
        for (ProductionTask child : children) {
            ProductionTask parent = myTaskById.get(child.getParentTaskId());
            if (parent == null) continue;
            MyProductionExecutionVO vo = byExecution.get(parent.getExecutionId());
            BigDecimal completed = childSubtree
                    .getOrDefault(child.getTaskId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[0];
            vo.setChildCompletedQuantity(vo.getChildCompletedQuantity().add(completed));
            vo.setChildProcessingQuantity(vo.getChildProcessingQuantity()
                    .add(floorZero(zero(child.getTaskQuantity()).subtract(completed))));
            vo.setPendingMyApprovalQuantity(vo.getPendingMyApprovalQuantity()
                    .add(childPending.getOrDefault(child.getTaskId(), BigDecimal.ZERO)));
        }
        result.setRecords(executionIds.stream().map(byExecution::get).filter(Objects::nonNull).toList());
        return result;
    }

    private Map<Long, BigDecimal> reportTotals(List<Long> taskIds, String status) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) return totals;
        String ids = taskIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.query("SELECT task_id,COALESCE(SUM(qualified_quantity+defective_quantity),0) total"
                        + " FROM production_work_report WHERE task_id IN (" + ids + ")"
                        + " AND report_status=? GROUP BY task_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        totals.put(rs.getLong("task_id"), rs.getBigDecimal("total")), status);
        return totals;
    }

    @Override
    public ChildProcessingDetailVO getMyChildProcessingDetail(Long executionId) {
        if (executionId == null) throw new BusinessException("executionId 不能为空");
        Long userId = SecurityUtils.getUserId();
        List<ProductionTask> myTasks = productionTaskMapper.selectList(Wrappers.<ProductionTask>lambdaQuery()
                .eq(ProductionTask::getExecutionId, executionId)
                .eq(ProductionTask::getAssigneeId, userId)
                .ne(ProductionTask::getStatus, STATUS_CANCELLED));
        if (myTasks.isEmpty()) {
            throw new BusinessException("当前用户在该工序下没有有效生产任务");
        }
        List<Long> myTaskIds = myTasks.stream().map(ProductionTask::getTaskId).toList();
        List<ProductionTask> children = productionTaskMapper.selectList(Wrappers.<ProductionTask>lambdaQuery()
                .in(ProductionTask::getParentTaskId, myTaskIds)
                .ne(ProductionTask::getStatus, STATUS_CANCELLED)
                .orderByAsc(ProductionTask::getTaskId));
        List<Long> childIds = children.stream().map(ProductionTask::getTaskId).toList();
        Map<Long, BigDecimal[]> childSubtree = subtreeAggregates(childIds);
        Map<Long, BigDecimal> childPending = reportTotals(childIds, REPORT_STATUS_PENDING);

        Map<Long, String[]> assignees = new HashMap<>();
        Set<Long> assigneeIds = children.stream().map(ProductionTask::getAssigneeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!assigneeIds.isEmpty()) {
            String ids = assigneeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.query("SELECT u.user_id,COALESCE(NULLIF(u.nick_name,''),u.user_name) assignee_name,"
                            + " d.dept_name FROM sys_user u LEFT JOIN sys_dept d ON d.dept_id=u.dept_id"
                            + " WHERE u.user_id IN (" + ids + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> assignees.put(rs.getLong("user_id"),
                            new String[]{rs.getString("assignee_name"), rs.getString("dept_name")}));
        }

        ChildProcessingDetailVO detail = new ChildProcessingDetailVO();
        detail.setExecutionId(executionId);
        detail.setMyResponsibilityQuantity(myTasks.stream().map(ProductionTask::getTaskQuantity)
                .map(this::zero).reduce(BigDecimal.ZERO, BigDecimal::add));
        detail.setChildCompletedQuantity(BigDecimal.ZERO);
        detail.setChildProcessingQuantity(BigDecimal.ZERO);
        detail.setPendingMyApprovalQuantity(BigDecimal.ZERO);
        jdbcTemplate.query("SELECT o.order_no,COALESCE(NULLIF(e.process_name,''),p.process_name) process_name,"
                        + " e.execution_status FROM production_operation_execution e"
                        + " LEFT JOIN production_order o ON o.order_id=e.order_id"
                        + " LEFT JOIN engineering_standard_process p ON p.process_id=e.process_id"
                        + " WHERE e.execution_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    detail.setOrderNo(rs.getString("order_no"));
                    detail.setProcessName(rs.getString("process_name"));
                    detail.setExecutionStatus(rs.getInt("execution_status"));
                }, executionId);
        List<ChildProcessingDetailVO.Record> records = new ArrayList<>();
        for (ProductionTask child : children) {
            BigDecimal completed = childSubtree
                    .getOrDefault(child.getTaskId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[0];
            BigDecimal pending = childPending.getOrDefault(child.getTaskId(), BigDecimal.ZERO);
            BigDecimal processing = floorZero(zero(child.getTaskQuantity()).subtract(completed));
            ChildProcessingDetailVO.Record record = new ChildProcessingDetailVO.Record();
            record.setTaskId(child.getTaskId());
            record.setTaskNo(child.getTaskNo());
            record.setAssigneeId(child.getAssigneeId());
            String[] assignee = assignees.get(child.getAssigneeId());
            record.setAssigneeName(assignee == null ? null : assignee[0]);
            record.setDepartmentName(assignee == null ? null : assignee[1]);
            record.setTaskQuantity(zero(child.getTaskQuantity()));
            record.setCompletedQuantity(completed);
            record.setPendingApprovalQuantity(pending);
            record.setProcessingQuantity(processing);
            record.setStatus(child.getStatus());
            record.setStatusLabel(statusLabel(child.getStatus()));
            records.add(record);
            detail.setChildCompletedQuantity(detail.getChildCompletedQuantity().add(completed));
            detail.setChildProcessingQuantity(detail.getChildProcessingQuantity().add(processing));
            detail.setPendingMyApprovalQuantity(detail.getPendingMyApprovalQuantity().add(pending));
        }
        detail.setRecords(records);
        return detail;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public List<TaskEventVO> listEvents(Long taskId) {
        if (productionTaskMapper.selectById(taskId) == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        return productionTaskMapper.selectTaskEvents(taskId);
    }

    @Override
    public List<TaskTreeRowVO> listMyTasks(Long executionId) {
        Long userId;
        try {
            userId = SecurityUtils.getUserId();
        } catch (Exception e) {
            throw new BusinessException("无法获取当前登录人");
        }
        if (userId == null) {
            throw new BusinessException("无法获取当前登录人");
        }
        LambdaQueryWrapper<ProductionTask> wrapper = Wrappers.<ProductionTask>lambdaQuery()
                .eq(ProductionTask::getAssigneeId, userId)
                .ne(ProductionTask::getStatus, STATUS_CANCELLED)
                .ne(ProductionTask::getStatus, STATUS_COMPLETED)
                .orderByDesc(ProductionTask::getTaskId);
        if (executionId != null) {
            wrapper.eq(ProductionTask::getExecutionId, executionId);
        }
        return project(productionTaskMapper.selectList(wrapper), null);
    }

    @Override
    public TaskTreeRowVO getDetail(Long taskId) {
        ProductionTask task = productionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        List<TaskTreeRowVO> rows = project(List.of(task), null);
        return rows.get(0);
    }

    @Override
    public TaskTreeRowVO getFirstTaskByExecution(Long executionId) {
        if (executionId == null) {
            throw new BusinessException("executionId 不能为空");
        }
        Long firstTaskId = findFirstTask(executionId);
        return firstTaskId == null ? null : getDetail(firstTaskId);
    }

    @Override
    public void assertExecutionCompletable(Long executionId) {
        if (executionId == null) {
            throw new BusinessException("executionId 不能为空");
        }
        Long firstTaskId = findFirstTask(executionId);
        if (firstTaskId == null) {
            throw new BusinessException("工序尚未创建 First Task，不能完成");
        }
        ProductionTask firstTask = productionTaskMapper.selectById(firstTaskId);
        if (firstTask == null || !STATUS_COMPLETED.equals(firstTask.getStatus())) {
            throw new BusinessException("请先完成 First Task 及其全部下级责任（含报工审批）");
        }
    }

    @Override
    public List<TaskTreeRowVO> listChildren(Long taskId) {
        if (productionTaskMapper.selectById(taskId) == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        List<ProductionTask> children = productionTaskMapper.selectList(Wrappers.<ProductionTask>lambdaQuery()
                .eq(ProductionTask::getParentTaskId, taskId)
                .ne(ProductionTask::getStatus, STATUS_CANCELLED)
                .orderByDesc(ProductionTask::getTaskId));
        return project(children, taskId);
    }

    @Override
    public List<TaskCompletionDetailVO> listCompletionDetails(Long taskId) {
        if (productionTaskMapper.selectById(taskId) == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        try {
            return jdbcTemplate.query(
                    "WITH RECURSIVE sub AS ("
                            + " SELECT task_id FROM production_task WHERE task_id = ?"
                            + " UNION ALL"
                            + " SELECT c.task_id FROM production_task c"
                            + " JOIN sub s ON c.parent_task_id = s.task_id"
                            + " WHERE c.status != 'CANCELLED'"
                            + ") SELECT wr.report_id, wr.task_id, t.assignee_id AS task_assignee_id,"
                            + " COALESCE(NULLIF(u.nick_name, ''), u.user_name) AS task_assignee_name,"
                            + " wr.reporter_id, wr.reporter_name, wr.execution_id, wr.order_no,"
                            + " e.process_name, wr.qualified_quantity, wr.defective_quantity,"
                            + " wr.qualified_quantity + wr.defective_quantity AS report_quantity,"
                            + " wr.report_time, wr.reviewer_name, wr.review_time, wr.remark"
                            + " FROM production_work_report wr"
                            + " JOIN sub ON sub.task_id = wr.task_id"
                            + " JOIN production_task t ON t.task_id = wr.task_id"
                            + " LEFT JOIN sys_user u ON u.user_id = t.assignee_id"
                            + " LEFT JOIN production_operation_execution e ON e.execution_id = wr.execution_id"
                            + " WHERE wr.report_status = 'APPROVED'"
                            + " ORDER BY wr.report_time DESC, wr.report_id DESC",
                    (rs, i) -> {
                        TaskCompletionDetailVO vo = new TaskCompletionDetailVO();
                        vo.setReportId(rs.getLong("report_id"));
                        vo.setTaskId(rs.getLong("task_id"));
                        vo.setTaskAssigneeId(rs.getObject("task_assignee_id") == null ? null : rs.getLong("task_assignee_id"));
                        vo.setTaskAssigneeName(rs.getString("task_assignee_name"));
                        vo.setReporterId(rs.getLong("reporter_id"));
                        vo.setReporterName(rs.getString("reporter_name"));
                        vo.setExecutionId(rs.getLong("execution_id"));
                        vo.setOrderNo(rs.getString("order_no"));
                        vo.setProcessName(rs.getString("process_name"));
                        vo.setQualifiedQuantity(rs.getBigDecimal("qualified_quantity"));
                        vo.setDefectiveQuantity(rs.getBigDecimal("defective_quantity"));
                        vo.setReportQuantity(rs.getBigDecimal("report_quantity"));
                        vo.setReportTime(rs.getTimestamp("report_time") == null ? null
                                : rs.getTimestamp("report_time").toLocalDateTime());
                        vo.setReviewerName(rs.getString("reviewer_name"));
                        vo.setReviewTime(rs.getTimestamp("review_time") == null ? null
                                : rs.getTimestamp("review_time").toLocalDateTime());
                        vo.setRemark(rs.getString("remark"));
                        return vo;
                    }, taskId);
        } catch (Exception e) {
            log.warn("查询完成明细失败 taskId={}: {}", taskId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public BigDecimal remainingQuantity(Long taskId) {
        ProductionTask task = productionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        return currentRemaining(task, effectiveChildSum(taskId));
    }

    // ==================== P2 Task Flow ====================

    @Override
    public List<TaskCandidateVO> listCandidates(Long taskId) {
        ProductionTask task = productionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        if (task.getAssigneeId() == null) {
            // 首次分配：仅生产管理者拥有首次分配权限；候选树根 = 当前登录人（自己 + 全部层级下属）
            if (!SecurityUtils.hasRole(ROLE_PRODUCTION_MANAGER)) {
                return new ArrayList<>();
            }
            Long loginUserId;
            try {
                loginUserId = SecurityUtils.getUserId();
            } catch (Exception e) {
                return new ArrayList<>();
            }
            return assigneeResolver.listAssignableUsers(loginUserId);
        }
        // 已分配：候选树根 = assignee（分配动作必须由 assignee 本人发起，assign 内校验）
        return assigneeResolver.listAssignableUsers(task.getAssigneeId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long taskId, TaskAssignDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("分配明细不能为空");
        }
        // 父行 FOR UPDATE：串行化同一 Task 的并发分配（child INSERT 需在父行取共享锁，与 X 锁互斥）
        ProductionTask task = lockTask(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("任务已取消，不能分配");
        }
        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("任务已完成，不能分配");
        }

        // ===== 统一分配：每个层级行为同构，一次事务校验并创建全部 Child，任一失败整体回滚 =====
        // 身份门（唯一层级差异）：未分配（assignee_id IS NULL）→ 仅生产管理者可发起；已分配 → 必须由当前执行人发起
        Long loginUserId;
        try {
            loginUserId = SecurityUtils.getUserId();
        } catch (Exception e) {
            throw new BusinessException("无法获取当前登录人");
        }
        if (loginUserId == null) {
            throw new BusinessException("无法获取当前登录人");
        }
        if (task.getAssigneeId() == null) {
            if (!SecurityUtils.hasRole(ROLE_PRODUCTION_MANAGER)) {
                throw new BusinessException("仅生产管理者可进行首次分配");
            }
        } else if (!loginUserId.equals(task.getAssigneeId())) {
            throw new BusinessException("仅当前任务执行人可分配");
        }
        // 无可选下属 → 无分配权限（自己只作为候选树根展示，不可分配给自己）
        if (!assigneeResolver.hasAssignableSubordinates(loginUserId)) {
            throw new BusinessException("当前人员无可分配下属，无分配权限");
        }
        for (TaskAssignItemDTO item : dto.getItems()) {
            if (item.getAssigneeId() == null) {
                throw new BusinessException("执行人必填");
            }
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("分配数量必须大于0");
            }
            if (!assigneeResolver.isAssignableTo(loginUserId, item.getAssigneeId())) {
                throw new BusinessException("目标人员不在可分配范围: " + item.getAssigneeId());
            }
        }
        BigDecimal total = dto.getItems().stream()
                .map(TaskAssignItemDTO::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = currentRemaining(task, effectiveChildSum(taskId));
        if (total.compareTo(remaining) > 0) {
            throw new BusinessException("分配总量超过当前剩余，剩余可分配: " + remaining);
        }

        // 状态推进：PENDING → ACTIVE（首次真正分配出 Child，进入责任执行；P5 状态不再由 assignee_id 定义）；
        // 已 ACTIVE 仅 bump version（修改检测）
        int bumped;
        if (STATUS_PENDING.equals(task.getStatus())) {
            bumped = productionTaskMapper.activateIfPending(taskId, task.getVersion());
        } else {
            bumped = productionTaskMapper.bumpVersion(taskId, task.getVersion());
        }
        if (bumped != 1) {
            throw new BusinessException("任务已被其他操作修改，请刷新后重试");
        }

        for (TaskAssignItemDTO item : dto.getItems()) {
            ProductionTask child = new ProductionTask();
            child.setTaskNo(nextTaskNo(task.getExecutionId(), false));
            child.setExecutionId(task.getExecutionId());
            child.setParentTaskId(taskId);
            child.setAssigneeId(item.getAssigneeId());
            child.setTaskQuantity(item.getQuantity());
            child.setStatus(STATUS_ACTIVE);
            child.setVersion(0);
            productionTaskMapper.insert(child);
            recordEvent(taskId, child.getTaskId(), ACTION_ASSIGN, task.getAssigneeId(), item.getAssigneeId(),
                    item.getQuantity(), task.getTaskQuantity(), task.getTaskQuantity(), dto.getRemark());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recall(Long parentTaskId, TaskRecallDTO dto) {
        if (dto == null || dto.getChildTaskId() == null) {
            throw new BusinessException("子任务必填");
        }
        if (dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("收回数量必须大于0");
        }
        // 锁顺序：父 → 直接子任务 → … → 目标子任务（自顶向下，避免死锁）
        ProductionTask parent = lockTask(parentTaskId);
        if (parent == null) {
            throw new BusinessException("父任务不存在: " + parentTaskId);
        }
        if (STATUS_COMPLETED.equals(parent.getStatus())) {
            throw new BusinessException("父任务已完成，不能收回");
        }
        assertBusinessOperator(parent);

        // 目标子任务允许任意层级后代；先普通读确定路径，再按 父→…→child 顺序加锁
        ProductionTask childView = productionTaskMapper.selectById(dto.getChildTaskId());
        if (childView == null) {
            throw new BusinessException("子任务不存在: " + dto.getChildTaskId());
        }
        // 向上收集中间层 id（child.parent → … → 直接子任务），校验属于本任务后代
        List<Long> middleIds = new ArrayList<>();
        Long cursor = childView.getParentTaskId();
        while (cursor != null && !cursor.equals(parentTaskId)) {
            ProductionTask p = productionTaskMapper.selectById(cursor);
            if (p == null) {
                throw new BusinessException("任务链不存在: " + cursor);
            }
            middleIds.add(cursor);
            cursor = p.getParentTaskId();
        }
        if (cursor == null) {
            throw new BusinessException("只能从本任务的下级任务收回，禁止跨树");
        }
        // 自顶向下加锁：直接子任务 → … → 目标子任务
        List<ProductionTask> chain = new ArrayList<>();
        for (int i = middleIds.size() - 1; i >= 0; i--) {
            ProductionTask m = lockTask(middleIds.get(i));
            if (m == null) {
                throw new BusinessException("中间任务不存在: " + middleIds.get(i));
            }
            chain.add(m);
        }
        ProductionTask child = lockTask(dto.getChildTaskId());
        if (child == null) {
            throw new BusinessException("子任务不存在: " + dto.getChildTaskId());
        }
        // 责任链保护：路径上任何节点已完成/已取消则不可收回
        for (ProductionTask m : chain) {
            if (STATUS_COMPLETED.equals(m.getStatus()) || STATUS_CANCELLED.equals(m.getStatus())) {
                throw new BusinessException("任务链中存在已完成/已取消节点，不能收回");
            }
        }
        if (STATUS_CANCELLED.equals(child.getStatus())) {
            throw new BusinessException("子任务已取消，无可收回数量");
        }
        if (STATUS_COMPLETED.equals(child.getStatus())) {
            throw new BusinessException("子任务已完成，无可收回数量");
        }
        BigDecimal childRemaining = currentRemaining(child, effectiveChildSum(child.getTaskId()));
        if (dto.getQuantity().compareTo(childRemaining) > 0) {
            throw new BusinessException("收回数量超过子任务当前剩余: " + childRemaining);
        }
        // 扣减目标子任务 + 沿路径逐级扣减中间层（量最终归集到当前工单 remaining；中间层 remaining 投影不变）
        BigDecimal afterQuantity = child.getTaskQuantity().subtract(dto.getQuantity());
        String newStatus = afterQuantity.compareTo(BigDecimal.ZERO) == 0 ? STATUS_CANCELLED : child.getStatus();
        int affected = productionTaskMapper.decreaseQuantity(child.getTaskId(), dto.getQuantity(), newStatus, child.getVersion());
        if (affected != 1) {
            throw new BusinessException("子任务已被其他操作修改，请刷新后重试");
        }
        for (ProductionTask m : chain) {
            BigDecimal after = m.getTaskQuantity().subtract(dto.getQuantity());
            String st = after.compareTo(BigDecimal.ZERO) == 0 ? STATUS_CANCELLED : m.getStatus();
            if (productionTaskMapper.decreaseQuantity(m.getTaskId(), dto.getQuantity(), st, m.getVersion()) != 1) {
                throw new BusinessException("任务链已被其他操作修改，请刷新后重试");
            }
        }
        recordEvent(parentTaskId, child.getTaskId(), ACTION_RECALL, child.getAssigneeId(), parent.getAssigneeId(),
                dto.getQuantity(), parent.getTaskQuantity(), parent.getTaskQuantity(), dto.getRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(Long taskId, TaskReturnDTO dto) {
        if (dto == null || dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退回数量必须大于0");
        }
        // 先读父任务ID（parent_task_id 不可变），再按 父 → 子 顺序加锁
        ProductionTask taskView = productionTaskMapper.selectById(taskId);
        if (taskView == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        Long parentId = taskView.getParentTaskId();
        if (parentId == null) {
            throw new BusinessException("第一层任务无父任务，禁止退回");
        }
        ProductionTask parent = lockTask(parentId);
        if (parent == null) {
            throw new BusinessException("父任务不存在: " + parentId);
        }
        if (STATUS_COMPLETED.equals(parent.getStatus())) {
            throw new BusinessException("父任务已完成，不能退回");
        }
        ProductionTask task = lockTask(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("任务已取消，无可退回数量");
        }
        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("任务已完成，无可退回数量");
        }
        assertBusinessOperator(task);
        BigDecimal remaining = currentRemaining(task, effectiveChildSum(taskId));
        if (dto.getQuantity().compareTo(remaining) > 0) {
            throw new BusinessException("退回数量超过当前剩余: " + remaining);
        }
        BigDecimal afterQuantity = task.getTaskQuantity().subtract(dto.getQuantity());
        String newStatus = afterQuantity.compareTo(BigDecimal.ZERO) == 0 ? STATUS_CANCELLED : task.getStatus();
        int affected = productionTaskMapper.decreaseQuantity(taskId, dto.getQuantity(), newStatus, task.getVersion());
        if (affected != 1) {
            throw new BusinessException("任务已被其他操作修改，请刷新后重试");
        }
        recordEvent(taskId, parentId, ACTION_RETURN, task.getAssigneeId(), parent.getAssigneeId(),
                dto.getQuantity(), task.getTaskQuantity(), afterQuantity, dto.getRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long taskId, TaskCompleteDTO dto) {
        ProductionTask task = lockTask(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        assertBusinessOperator(task);
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("任务已取消，不能完成");
        }
        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("任务已完成，无需重复完成");
        }
        if (!STATUS_ACTIVE.equals(task.getStatus())) {
            throw new BusinessException("任务未进入责任执行，不能完成");
        }
        // P5 完成前置（人工确认链，全部满足才允许）：
        // subtreeCompleted == taskQuantity && subtreePending == 0 && remaining == 0
        // && assigned 未完成责任 == 0 && 所有有效直接 Child 均已 COMPLETED
        BigDecimal childAssigned = effectiveChildSum(taskId);
        BigDecimal ownPending = pendingQuantity(task);
        BigDecimal ownCompleted = completedQuantity(task);
        BigDecimal remaining = floorZero(task.getTaskQuantity().subtract(childAssigned)
                .subtract(ownPending).subtract(ownCompleted));
        BigDecimal[] sub = subtreeAggregates(List.of(taskId))
                .getOrDefault(taskId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        BigDecimal subtreeCompleted = sub[0];
        BigDecimal subtreePending = sub[1];
        BigDecimal assignedOutstanding = floorZero(childAssigned
                .subtract(subtreeCompleted.subtract(ownCompleted))
                .subtract(subtreePending.subtract(ownPending)));
        Long incompleteChildren = productionTaskMapper.countIncompleteChildren(taskId);
        if (subtreeCompleted.compareTo(task.getTaskQuantity()) != 0) {
            throw new BusinessException("有效完成量未达到任务数量，不能完成");
        }
        if (subtreePending.signum() > 0) {
            throw new BusinessException("存在待审批报工，不能完成");
        }
        if (remaining.signum() > 0) {
            throw new BusinessException("存在剩余数量，不能完成");
        }
        if (assignedOutstanding.signum() > 0) {
            throw new BusinessException("存在未完成的下发责任，不能完成");
        }
        if (incompleteChildren != null && incompleteChildren > 0) {
            throw new BusinessException("存在未完成（非 COMPLETED）的直接子任务，须全部完成后父任务才能完成");
        }
        int affected = productionTaskMapper.markCompleted(taskId, task.getVersion());
        if (affected != 1) {
            throw new BusinessException("任务已被其他操作修改，请刷新后重试");
        }
        recordEvent(taskId, null, ACTION_COMPLETE, task.getAssigneeId(), task.getAssigneeId(),
                BigDecimal.ZERO, task.getTaskQuantity(), task.getTaskQuantity(),
                dto == null ? null : dto.getRemark());
    }

    // ============ 私有方法 ============

    /**
     * 在当前工序执行行锁内领取下一个 Task 流水并生成全局唯一业务编号。
     * First Task 的存在性检查也在同一把 execution 行锁内完成，避免重复创建时空耗流水。
     */
    private String nextTaskNo(Long executionId, boolean firstTask) {
        Map<String, Object> context = productionOperationExecutionMapper
                .selectTaskNoContextForUpdate(executionId);
        if (context == null || context.isEmpty()) {
            throw new BusinessException("工序执行不存在: " + executionId);
        }
        if (firstTask && findFirstTask(executionId) != null) {
            return null;
        }
        Object orderNoValue = context.get("orderNo");
        Object processOrderValue = context.get("processOrder");
        Object taskSeqValue = context.get("taskSeq");
        if (orderNoValue == null || processOrderValue == null || taskSeqValue == null) {
            throw new BusinessException("工序执行缺少任务编号上下文: " + executionId);
        }
        if (productionOperationExecutionMapper.incrementTaskSeq(executionId) != 1) {
            throw new BusinessException("任务流水生成失败，请重试");
        }
        long taskSeq = ((Number) taskSeqValue).longValue() + 1;
        int processOrder = ((Number) processOrderValue).intValue();
        return orderNoValue + "-P" + String.format("%02d", processOrder)
                + "-T" + String.format("%03d", taskSeq);
    }

    private Long findFirstTask(Long executionId) {
        ProductionTask first = productionTaskMapper.selectOne(Wrappers.<ProductionTask>lambdaQuery()
                .eq(ProductionTask::getExecutionId, executionId)
                .isNull(ProductionTask::getParentTaskId));
        return first == null ? null : first.getTaskId();
    }

    /** 行锁：SELECT ... FOR UPDATE（写动作锁读取最新已提交状态） */
    private ProductionTask lockTask(Long taskId) {
        return productionTaskMapper.selectByIdForUpdate(taskId);
    }

    /** Σ直接有效 Child task_quantity（活动树投影，排除 CANCELLED） */
    private BigDecimal effectiveChildSum(Long taskId) {
        BigDecimal sum = productionTaskMapper.sumEffectiveChildQuantity(taskId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /**
     * P4：递归 CTE 批量子树聚合（一次查询覆盖整批 root，避免 N+1）
     * 返回 Map<rootId, BigDecimal[]{subtreeCompleted, subtreePending}>（有效树排除 CANCELLED；
     * CANCELLED 节点由 P2/P3 gate 保证 quantity=0 且无 APPROVED/PENDING，排除后不影响生产事实归集）
     */
    private Map<Long, BigDecimal[]> subtreeAggregates(List<Long> rootIds) {
        Map<Long, BigDecimal[]> map = new HashMap<>();
        if (rootIds == null || rootIds.isEmpty()) {
            return map;
        }
        String idStr = rootIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            jdbcTemplate.query(
                    "WITH RECURSIVE sub AS ("
                            + " SELECT task_id AS root_id, task_id FROM production_task WHERE task_id IN (" + idStr + ")"
                            + " UNION ALL"
                            + " SELECT s.root_id, c.task_id FROM production_task c"
                            + " JOIN sub s ON c.parent_task_id = s.task_id"
                            + " WHERE c.status != 'CANCELLED'"
                            + ") SELECT sub.root_id,"
                            + " COALESCE(SUM(CASE WHEN wr.report_status = 'APPROVED'"
                            + " THEN wr.qualified_quantity + wr.defective_quantity ELSE 0 END), 0) AS subtree_completed,"
                            + " COALESCE(SUM(CASE WHEN wr.report_status = 'PENDING'"
                            + " THEN wr.qualified_quantity + wr.defective_quantity ELSE 0 END), 0) AS subtree_pending"
                            + " FROM sub"
                            + " LEFT JOIN production_work_report wr ON wr.task_id = sub.task_id"
                            + " GROUP BY sub.root_id",
                    rs -> {
                        map.put(rs.getLong("root_id"), new BigDecimal[]{
                                rs.getBigDecimal("subtree_completed"), rs.getBigDecimal("subtree_pending")});
                    });
        } catch (Exception e) {
            log.warn("查询子树聚合失败: {}", e.getMessage());
        }
        return map;
    }

    /** P3：当前 Task 自身待审批占用量 = SUM(qualified+defective) WHERE task_id AND status=PENDING */
    private BigDecimal pendingQuantity(ProductionTask task) {
        return workReportSum(task.getTaskId(), REPORT_STATUS_PENDING);
    }

    /** P3：当前 Task 自身有效完成量 = SUM(qualified+defective) WHERE task_id AND status=APPROVED */
    private BigDecimal completedQuantity(ProductionTask task) {
        return workReportSum(task.getTaskId(), REPORT_STATUS_APPROVED);
    }

    /** WorkReport 数量聚合（当前 Task 自己；不混入子树，P4 才做归集展示） */
    private BigDecimal workReportSum(Long taskId, String reportStatus) {
        if (taskId == null) return BigDecimal.ZERO;
        BigDecimal v = productionWorkReportMapper.sumTaskQuantityByStatus(taskId, reportStatus);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 当前任务自身剩余 = taskQuantity - assigned - pending - completed（下限 0；P3 真实 WorkReport 投影） */
    private BigDecimal currentRemaining(ProductionTask task, BigDecimal assigned) {
        return floorZero(task.getTaskQuantity().subtract(assigned)
                .subtract(pendingQuantity(task)).subtract(completedQuantity(task)));
    }

    /**
     * P5 业务身份门（不使用 *:*:* / admin 全局豁免；系统管理员不自动获得普通生产业务动作权限）：
     * - assignee_id IS NULL（如第一层）→ 仅真实生产管理者（production:all）可执行
     * - 已分配 → 当前执行人，或生产管理者（业务监督身份）
     */
    private void assertBusinessOperator(ProductionTask task) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException("无法获取当前登录人");
        }
        if (task.getAssigneeId() == null) {
            if (!SecurityUtils.hasRole(ROLE_PRODUCTION_MANAGER)) {
                throw new BusinessException("仅生产管理者可执行该操作");
            }
            return;
        }
        if (!userId.equals(task.getAssigneeId()) && !SecurityUtils.hasRole(ROLE_PRODUCTION_MANAGER)) {
            throw new BusinessException("仅当前任务执行人或生产管理者可执行该操作");
        }
    }

    /** 写入 TaskEvent（与 Task 修改同一事务；before/after 唯一语义 = event.taskId 的 task_quantity 前后值） */
    private void recordEvent(Long taskId, Long relatedTaskId, String action, Long fromAssigneeId, Long toAssigneeId,
                             BigDecimal quantity, BigDecimal beforeTaskQuantity, BigDecimal afterTaskQuantity,
                             String remark) {
        ProductionTaskEvent event = new ProductionTaskEvent();
        event.setTaskId(taskId);
        event.setRelatedTaskId(relatedTaskId);
        event.setAction(action);
        event.setOperatorId(SecurityUtils.getUserId());
        event.setOperatorName(operatorName());
        event.setFromAssigneeId(fromAssigneeId);
        event.setToAssigneeId(toAssigneeId);
        event.setQuantity(quantity);
        event.setBeforeTaskQuantity(beforeTaskQuantity);
        event.setAfterTaskQuantity(afterTaskQuantity);
        event.setRemark(remark);
        productionTaskEventMapper.insert(event);
        log.info("TaskEvent 写入: action={} task={} related={} quantity={} before={} after={}",
                action, taskId, relatedTaskId, quantity, beforeTaskQuantity, afterTaskQuantity);
    }

    private static String operatorName() {
        String realName = SecurityUtils.getRealName();
        return realName != null && !realName.isBlank() ? realName : SecurityUtils.getUsername();
    }

    /**
     * 统一投影（避免 N+1）：execution 上下文 / 工单号 / 工序信息 / 执行人姓名 /
     * 上级执行人姓名 / Σ直接有效子节点（assignedQuantity + hasChildren，排除 CANCELLED）
     */
    private List<TaskTreeRowVO> project(List<ProductionTask> tasks, Long knownParentTaskId) {
        List<TaskTreeRowVO> rows = new ArrayList<>();
        if (tasks == null || tasks.isEmpty()) {
            return rows;
        }
        List<Long> taskIds = tasks.stream().map(ProductionTask::getTaskId).collect(Collectors.toList());
        String taskIdStr = taskIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 1) execution 上下文
        Set<Long> execIds = tasks.stream().map(ProductionTask::getExecutionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Object[]> execMap = new HashMap<>();
        Map<Long, String> orderNoMap = new HashMap<>();
        Map<Long, String[]> processMap = new HashMap<>();
        if (!execIds.isEmpty()) {
            String execIdStr = execIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                jdbcTemplate.query("SELECT e.execution_id, e.order_id, e.process_id,"
                                + " COALESCE(NULLIF(e.process_name,''),p.process_name) process_name, e.process_order"
                                + " FROM production_operation_execution e"
                                + " LEFT JOIN engineering_standard_process p ON p.process_id = e.process_id"
                                + " WHERE e.execution_id IN (" + execIdStr + ")",
                        rs -> {
                            Long eid = rs.getLong("execution_id");
                            execMap.put(eid, new Object[]{
                                    rs.getLong("order_id"),
                                    rs.getObject("process_id"),
                                    rs.getString("process_name"),
                                    rs.getObject("process_order")});
                        });
            } catch (Exception e) {
                log.warn("查询 execution 上下文失败: {}", e.getMessage());
            }
            Set<Long> orderIds = execMap.values().stream()
                    .map(a -> a[0] == null ? null : ((Number) a[0]).longValue())
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!orderIds.isEmpty()) {
                String orderIdStr = orderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                try {
                    jdbcTemplate.query("SELECT order_id, order_no FROM production_order WHERE order_id IN (" + orderIdStr + ")",
                            rs -> {
                                orderNoMap.put(rs.getLong("order_id"), rs.getString("order_no"));
                            });
                } catch (Exception e) {
                    log.warn("查询工单号失败: {}", e.getMessage());
                }
            }
            Set<Long> processIds = execMap.values().stream()
                    .map(a -> a[1] == null ? null : ((Number) a[1]).longValue())
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!processIds.isEmpty()) {
                String pidStr = processIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                try {
                    jdbcTemplate.query("SELECT process_id, process_code, process_name FROM engineering_standard_process"
                                    + " WHERE process_id IN (" + pidStr + ")",
                            rs -> {
                                processMap.put(rs.getLong("process_id"),
                                        new String[]{rs.getString("process_code"), rs.getString("process_name")});
                            });
                } catch (Exception e) {
                    log.warn("查询工序信息失败: {}", e.getMessage());
                }
            }
        }

        // 2) 执行人姓名（assignee + 上级 assignee 合并批量查询）
        Set<Long> assigneeIds = tasks.stream().map(ProductionTask::getAssigneeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> parentIds = tasks.stream().map(ProductionTask::getParentTaskId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (knownParentTaskId != null) {
            parentIds.add(knownParentTaskId);
        }
        Map<Long, ProductionTask> parentMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            String parentIdStr = parentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                jdbcTemplate.query("SELECT task_id, assignee_id FROM production_task WHERE task_id IN (" + parentIdStr + ")",
                        rs -> {
                            ProductionTask pt = new ProductionTask();
                            pt.setTaskId(rs.getLong("task_id"));
                            pt.setAssigneeId(rs.getObject("assignee_id") == null ? null : rs.getLong("assignee_id"));
                            parentMap.put(pt.getTaskId(), pt);
                            if (pt.getAssigneeId() != null) {
                                assigneeIds.add(pt.getAssigneeId());
                            }
                        });
            } catch (Exception e) {
                log.warn("查询上级任务失败: {}", e.getMessage());
            }
        }
        Map<Long, String> userNameMap = new HashMap<>();
        if (!assigneeIds.isEmpty()) {
            String userIdStr = assigneeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                jdbcTemplate.query("SELECT user_id, COALESCE(NULLIF(nick_name, ''), user_name) AS display_name"
                                + " FROM sys_user WHERE user_id IN (" + userIdStr + ")",
                        rs -> {
                            userNameMap.put(rs.getLong("user_id"), rs.getString("display_name"));
                        });
            } catch (Exception e) {
                log.warn("查询用户姓名失败: {}", e.getMessage());
            }
        }

        // 3) Σ直接有效子节点（assignedQuantity + hasChildren；排除 CANCELLED）
        Map<Long, BigDecimal> childAssignedMap = new HashMap<>();
        Set<Long> childParentIds = new HashSet<>();
        try {
            jdbcTemplate.query("SELECT parent_task_id, COALESCE(SUM(task_quantity), 0) AS total, COUNT(*) AS cnt"
                            + " FROM production_task WHERE parent_task_id IN (" + taskIdStr + ")"
                            + " AND status != 'CANCELLED' GROUP BY parent_task_id",
                    rs -> {
                        Long pid = rs.getLong("parent_task_id");
                        childAssignedMap.put(pid, rs.getBigDecimal("total"));
                        childParentIds.add(pid);
                    });
        } catch (Exception e) {
            log.warn("查询子节点汇总失败: {}", e.getMessage());
        }

        // 3.3) 批量：parent → 未完成（非 COMPLETED）直接子节点数（P5 COMPLETE 前置投影，避免 N+1）
        Map<Long, Long> incompleteChildrenMap = new HashMap<>();
        try {
            for (Map<String, Object> rowMap : productionTaskMapper.countIncompleteChildrenMap(taskIds)) {
                Object pid = rowMap.get("parent_task_id");
                Object cnt = rowMap.get("cnt");
                if (pid != null && cnt != null) {
                    incompleteChildrenMap.put(((Number) pid).longValue(), ((Number) cnt).longValue());
                }
            }
        } catch (Exception e) {
            log.warn("查询未完成子节点数失败: {}", e.getMessage());
        }

        // 3.5) WorkReport 批量投影（P3：当前 Task 自己的 pending/completed；避免 N+1）
        Map<Long, BigDecimal> pendingMap = new HashMap<>();
        Map<Long, BigDecimal> completedMap = new HashMap<>();
        try {
            jdbcTemplate.query("SELECT task_id, report_status,"
                            + " COALESCE(SUM(qualified_quantity + defective_quantity), 0) AS total"
                            + " FROM production_work_report WHERE task_id IN (" + taskIdStr + ")"
                            + " AND report_status IN ('PENDING','APPROVED') GROUP BY task_id, report_status",
                    rs -> {
                        Long tid = rs.getLong("task_id");
                        String st = rs.getString("report_status");
                        if (REPORT_STATUS_PENDING.equals(st)) {
                            pendingMap.put(tid, rs.getBigDecimal("total"));
                        } else if (REPORT_STATUS_APPROVED.equals(st)) {
                            completedMap.put(tid, rs.getBigDecimal("total"));
                        }
                    });
        } catch (Exception e) {
            log.warn("查询 WorkReport 投影失败: {}", e.getMessage());
        }

        // 3.6) P4：递归 CTE 批量子树聚合（subtree completed/pending；一次查询覆盖整批，避免 N+1）
        Map<Long, BigDecimal[]> subtreeMap = subtreeAggregates(taskIds);

        // 3.7) canAssign 批量投影（P4.5：无下属 → 无分配动作）
        //      首次分配：当前登录人（须生产管理者）；已分配：assignee
        Set<Long> candidateAssignIds = new HashSet<>(assigneeIds);
        Long loginUserId = null;
        boolean loginIsProdMgr = false;
        try {
            loginUserId = SecurityUtils.getUserId();
            loginIsProdMgr = SecurityUtils.hasRole(ROLE_PRODUCTION_MANAGER);
        } catch (Exception e) {
            log.warn("获取登录人失败，首次分配能力视为无: {}", e.getMessage());
        }
        boolean hasFirstLevel = tasks.stream().anyMatch(t -> t.getParentTaskId() == null);
        if (hasFirstLevel && loginUserId != null && loginIsProdMgr) {
            candidateAssignIds.add(loginUserId);
        }
        Set<Long> canAssignIds = new HashSet<>();
        if (!candidateAssignIds.isEmpty()) {
            try {
                canAssignIds.addAll(productionTaskMapper.selectUsersHavingSubordinates(candidateAssignIds));
            } catch (Exception e) {
                log.warn("查询分配能力失败: {}", e.getMessage());
            }
        }

        // 4) 组装 VO
        for (ProductionTask t : tasks) {
            TaskTreeRowVO vo = new TaskTreeRowVO();
            vo.setTaskId(t.getTaskId());
            vo.setTaskNo(t.getTaskNo());
            vo.setParentTaskId(t.getParentTaskId());
            vo.setExecutionId(t.getExecutionId());
            Object[] exec = execMap.get(t.getExecutionId());
            if (exec != null) {
                if (exec[0] != null) {
                    vo.setOrderNo(orderNoMap.get(((Number) exec[0]).longValue()));
                }
                if (exec[1] != null) {
                    String[] pi = processMap.get(((Number) exec[1]).longValue());
                    if (pi != null) {
                        vo.setProcessCode(pi[0]);
                        vo.setProcessName(pi[1]);
                    }
                }
                if (vo.getProcessName() == null) {
                    vo.setProcessName((String) exec[2]);
                }
                vo.setProcessOrder(exec[3] == null ? null : ((Number) exec[3]).intValue());
            }
            vo.setAssigneeId(t.getAssigneeId());
            vo.setAssigneeName(t.getAssigneeId() == null ? null : userNameMap.get(t.getAssigneeId()));
            ProductionTask parent = parentMap.get(t.getParentTaskId());
            if (parent != null && parent.getAssigneeId() != null) {
                vo.setParentAssigneeName(userNameMap.get(parent.getAssigneeId()));
            }
            vo.setTaskQuantity(t.getTaskQuantity());
            BigDecimal ownPending = pendingMap.getOrDefault(t.getTaskId(), BigDecimal.ZERO);
            BigDecimal ownCompleted = completedMap.getOrDefault(t.getTaskId(), BigDecimal.ZERO);
            BigDecimal childAssigned = childAssignedMap.getOrDefault(t.getTaskId(), BigDecimal.ZERO);
            BigDecimal[] sub = subtreeMap.get(t.getTaskId());
            BigDecimal subtreeCompleted = sub == null ? BigDecimal.ZERO : sub[0];
            BigDecimal subtreePending = sub == null ? BigDecimal.ZERO : sub[1];
            // P4 展示口径：completed/pending = 整棵有效子树归集
            vo.setCompletedQuantity(subtreeCompleted);
            vo.setPendingQuantity(subtreePending);
            // P4 展示口径：assigned = 下游仍未 completed/pending 的有效责任量（禁止用于写 gate）
            BigDecimal assignedDisplay = childAssigned
                    .subtract(subtreeCompleted.subtract(ownCompleted))
                    .subtract(subtreePending.subtract(ownPending));
            vo.setAssignedQuantity(floorZero(assignedDisplay));
            // remaining 保持 gate 口径：taskQuantity - childAssigned - ownPending - ownCompleted
            BigDecimal remaining = floorZero(t.getTaskQuantity() == null ? BigDecimal.ZERO
                    : t.getTaskQuantity().subtract(childAssigned).subtract(ownPending).subtract(ownCompleted));
            vo.setRemainingQuantity(remaining);
            vo.setStatus(t.getStatus());
            vo.setStatusLabel(statusLabel(t.getStatus()));
            vo.setHasChildren(childParentIds.contains(t.getTaskId()));
            vo.setChildren(new ArrayList<>());
            // ASSIGN 身份门（与 assign() 后端一致）：未分配 → 仅生产管理者且具备可分配下属；
            // 已分配 → 仅当前执行人且具备可分配下属；生产管理者对已分配任务无分配权（P4.5 首次差异）
            boolean rowAssignAllowed;
            if (t.getAssigneeId() == null) {
                rowAssignAllowed = loginIsProdMgr && loginUserId != null && canAssignIds.contains(loginUserId);
            } else {
                rowAssignAllowed = loginUserId != null && loginUserId.equals(t.getAssigneeId())
                        && canAssignIds.contains(t.getAssigneeId());
            }
            vo.setAllowedActions(computeAllowedActions(t, ownPending, ownCompleted,
                    subtreeCompleted, subtreePending, childAssigned, remaining,
                    incompleteChildrenMap.getOrDefault(t.getTaskId(), 0L),
                    loginUserId, loginIsProdMgr, rowAssignAllowed));
            if (t.getAssigneeId() != null) {
                vo.setCanAssign(canAssignIds.contains(t.getAssigneeId()));
            } else {
                vo.setCanAssign(loginIsProdMgr && loginUserId != null && canAssignIds.contains(loginUserId));
            }
            rows.add(vo);
        }
        return rows;
    }

    /**
     * P5：allowedActions 统一投影（RBAC + 业务身份/责任关系 + 数量/结构，前端只按返回值渲染，不再自行拼业务判断）。
     * 身份规则：未分配（assignee_id IS NULL）→ 仅生产管理者；已分配 → 当前执行人或生产管理者。
     * ASSIGN 额外收口：未分配 → 仅生产管理者（且有可分配下属）；已分配 → 仅当前执行人（且有可分配下属）。
     * 终态（CANCELLED/COMPLETED）只保留 FLOW。
     */
    private List<String> computeAllowedActions(ProductionTask t, BigDecimal ownPending, BigDecimal ownCompleted,
                                               BigDecimal subtreeCompleted, BigDecimal subtreePending,
                                               BigDecimal childAssigned, BigDecimal remaining,
                                               long incompleteChildren, Long loginUserId, boolean loginIsProdMgr,
                                               boolean rowAssignAllowed) {
        List<String> actions = new ArrayList<>();
        actions.add("FLOW");
        if (t == null || STATUS_CANCELLED.equals(t.getStatus()) || STATUS_COMPLETED.equals(t.getStatus())) {
            return actions;
        }
        boolean active = STATUS_ACTIVE.equals(t.getStatus()) || STATUS_PENDING.equals(t.getStatus());
        boolean isOperator;
        if (t.getAssigneeId() == null) {
            isOperator = loginIsProdMgr;
        } else {
            isOperator = loginUserId != null && (loginUserId.equals(t.getAssigneeId()) || loginIsProdMgr);
        }
        if (!active || !isOperator) {
            return actions;
        }
        if (rowAssignAllowed && remaining.signum() > 0) {
            actions.add("ASSIGN");
        }
        if (t.getParentTaskId() != null && remaining.signum() > 0) {
            actions.add("RETURN");
        }
        if (childAssigned.signum() > 0) {
            actions.add("RECALL");
        }
        // COMPLETE：人工确认链（有效完成量达标 + 无 PENDING + 无剩余 + 无未完成下发责任 + 所有有效直接 Child 已 COMPLETED）
        BigDecimal assignedOutstanding = floorZero(childAssigned
                .subtract(subtreeCompleted.subtract(ownCompleted))
                .subtract(subtreePending.subtract(ownPending)));
        BigDecimal taskQuantity = t.getTaskQuantity() == null ? BigDecimal.ZERO : t.getTaskQuantity();
        if (STATUS_ACTIVE.equals(t.getStatus())
                && subtreeCompleted.compareTo(taskQuantity) == 0
                && subtreePending.signum() == 0
                && remaining.signum() == 0
                && assignedOutstanding.signum() == 0
                && incompleteChildren == 0) {
            actions.add("COMPLETE");
        }
        return actions;
    }

    private static String statusLabel(String status) {
        if (STATUS_PENDING.equals(status)) {
            return "未分配";
        }
        if (STATUS_ACTIVE.equals(status)) {
            return "进行中";
        }
        if (STATUS_COMPLETED.equals(status)) {
            return "已完成";
        }
        if (STATUS_CANCELLED.equals(status)) {
            return "已取消";
        }
        return status == null ? "未知" : status;
    }

    private static BigDecimal floorZero(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v;
    }
}
