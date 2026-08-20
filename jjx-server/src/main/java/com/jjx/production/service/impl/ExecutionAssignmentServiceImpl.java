package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.AssignmentCreateDTO;
import com.jjx.production.domain.dto.AssignmentReleaseDTO;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.entity.ProductionExecutionAssignment;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.AssignmentViewVO;
import com.jjx.production.enums.AssignmentStatusEnum;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionExecutionAssignmentMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.production.service.ExecutionAssignmentService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工序作业分配服务实现（WP-B）
 * <p>
 * 并发策略：单机事务 + 锁 Execution 行（SELECT ... FOR UPDATE），
 * 保证两个并发分配请求不会超分。不引入 Redis/分布式锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionAssignmentServiceImpl implements ExecutionAssignmentService {

    private final ProductionExecutionAssignmentMapper assignmentMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionDispatchNodeMapper nodeMapper;
    private final ProductionWorkReportMapper workReportMapper;
    private final DispatchNodeReadService dispatchNodeReadService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ==================== 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentViewVO create(AssignmentCreateDTO dto, String operatorName, Long operatorId) {
        if (dto == null || dto.getExecutionId() == null) {
            throw new BusinessException("缺少工序执行ID");
        }
        List<AssignmentCreateDTO.AssignmentItemDTO> items = dto.getAssignments();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少指定一名执行人及分配数量");
        }

        // 锁 Execution 行（并发：读-校验-写 串行化，防超分）
        ProductionOperationExecution exec = lockExecution(dto.getExecutionId());
        if (exec == null) {
            throw new BusinessException("工序执行记录不存在: " + dto.getExecutionId());
        }
        if (ExecutionStatusEnum.COMPLETED.getCode().equals(exec.getExecutionStatus())
                || ExecutionStatusEnum.CANCELLED.getCode().equals(exec.getExecutionStatus())) {
            throw new BusinessException("工序已完成/取消，不允许再分配作业");
        }

        // 权限：必须有 ACTIVE DispatchNode，且操作人是当前 ACTIVE 责任人
        // （超管遵循项目现有机制；普通用户即使有 assignment:add 权限，非责任人仍拒绝）
        ProductionDispatchNode activeNode = requireActiveNode(exec);
        checkAssignRight(activeNode, operatorId);

        // 数量校验：每项 > 0；批次合计 <= unassigned（整批原子）
        BigDecimal planned = exec.getInputQuantity() != null ? exec.getInputQuantity() : BigDecimal.ZERO;
        BigDecimal assigned = sumEffectiveByExecution(dto.getExecutionId());
        BigDecimal unassigned = planned.subtract(assigned);
        if (unassigned.compareTo(BigDecimal.ZERO) < 0) {
            unassigned = BigDecimal.ZERO;
        }

        // 批次内去重 + 与既有 ACTIVE Assignment 去重（WP-D 十一：同一 execution+user 同时最多一个有效 Assignment）
        java.util.Set<Long> batchUserIds = new java.util.HashSet<>();
        BigDecimal batchSum = BigDecimal.ZERO;
        for (AssignmentCreateDTO.AssignmentItemDTO item : items) {
            if (item.getAssigneeId() == null) {
                throw new BusinessException("执行人不能为空");
            }
            if (!batchUserIds.add(item.getAssigneeId())) {
                throw new BusinessException("同一批次中执行人重复：" + resolveAssigneeName(item.getAssigneeId()));
            }
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("分配数量必须大于 0");
            }
            batchSum = batchSum.add(item.getQuantity());
        }
        if (batchSum.compareTo(unassigned) > 0) {
            throw new BusinessException("分配数量合计 " + batchSum.stripTrailingZeros().toPlainString()
                    + " 超过剩余可分配 " + unassigned.stripTrailingZeros().toPlainString() + "，请调整");
        }
        // 既有 ACTIVE（非 CANCELLED）Assignment 去重：同 execution + user 已存在 → 拒绝，先释放/完成后才能再分
        for (AssignmentCreateDTO.AssignmentItemDTO item : items) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM production_execution_assignment "
                            + "WHERE execution_id = ? AND assignee_id = ? AND assignment_status <> 'CANCELLED'",
                    Integer.class, dto.getExecutionId(), item.getAssigneeId());
            if (exists != null && exists > 0) {
                throw new BusinessException("执行人 " + resolveAssigneeName(item.getAssigneeId())
                        + " 已有未完成/未释放的作业分配，请先完成或释放后再分配");
            }
        }

        // 写库（同一批次，原子）
        LocalDateTime now = LocalDateTime.now();
        for (AssignmentCreateDTO.AssignmentItemDTO item : items) {
            ProductionExecutionAssignment a = new ProductionExecutionAssignment();
            a.setExecutionId(dto.getExecutionId());
            a.setOrderId(exec.getOrderId());
            a.setDispatchId(activeNode.getDispatchId());
            a.setDispatchNodeId(activeNode.getNodeId());
            a.setAssigneeId(item.getAssigneeId());
            a.setAssigneeName(resolveAssigneeName(item.getAssigneeId()));
            a.setAssignedQuantity(item.getQuantity());
            a.setReleasedQuantity(BigDecimal.ZERO);
            a.setAssignmentStatus(AssignmentStatusEnum.ACTIVE.getCode());
            a.setAssignedBy(operatorId);
            a.setAssignedByName(operatorName);
            a.setAssignedAt(now);
            a.setCreateBy(operatorName);
            assignmentMapper.insert(a);
            log.info("创建作业分配: execution={} assignee={} qty={} node={}",
                    dto.getExecutionId(), item.getAssigneeId(), item.getQuantity(), activeNode.getNodeId());
        }

        return getByExecutionId(dto.getExecutionId());
    }

    // ==================== 释放剩余 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentViewVO release(Long assignmentId, AssignmentReleaseDTO dto, String operatorName, Long operatorId) {
        if (assignmentId == null) {
            throw new BusinessException("缺少作业分配ID");
        }
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new BusinessException("释放原因必填");
        }

        ProductionExecutionAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException("作业分配不存在: " + assignmentId);
        }
        if (!AssignmentStatusEnum.ACTIVE.getCode().equals(assignment.getAssignmentStatus())) {
            throw new BusinessException("仅有效(ACTIVE)作业分配可释放剩余");
        }

        // 锁 Execution 行（防并发释放叠加超量）
        ProductionOperationExecution exec = lockExecution(assignment.getExecutionId());
        if (exec == null) {
            throw new BusinessException("工序执行记录不存在");
        }

        // 权限：当前 ACTIVE 责任人（或超管）
        ProductionDispatchNode activeNode = requireActiveNode(exec);
        checkAssignRight(activeNode, operatorId);

        BigDecimal assigned = assignment.getAssignedQuantity();
        BigDecimal released = assignment.getReleasedQuantity() != null ? assignment.getReleasedQuantity() : BigDecimal.ZERO;
        BigDecimal reported = reportedQuantity(assignmentId);

        // effective = assigned - released；remaining = effective - reported
        BigDecimal effective = assigned.subtract(released);
        BigDecimal remaining = effective.subtract(reported);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该作业分配剩余数量为 0，无需释放");
        }

        // 释放：released += remaining（历史 assigned/reported 保留）
        assignment.setReleasedQuantity(released.add(remaining));
        assignment.setCancelledBy(operatorId);
        assignment.setCancelledAt(LocalDateTime.now());
        assignment.setCancelReason(dto.getReason());
        assignment.setUpdateBy(operatorName);
        // 行状态：整份有效数量已释放且无未报工剩余 → 语义上可视为 CANCELLED（若 reported=0 整份取消）
        if (reported.compareTo(BigDecimal.ZERO) <= 0) {
            assignment.setAssignmentStatus(AssignmentStatusEnum.CANCELLED.getCode());
        } else {
            // 部分报工后释放剩余：保留 ACTIVE（effective=reported，remaining=0 → 派生 COMPLETED）
            // 行状态保持 ACTIVE，派生状态由查询计算（避免把"部分生产+释放"解释成整份取消）
            assignment.setAssignmentStatus(AssignmentStatusEnum.ACTIVE.getCode());
        }
        assignmentMapper.updateById(assignment);
        log.info("释放作业剩余: assignment={} released={} reason={}",
                assignmentId, remaining.stripTrailingZeros().toPlainString(), dto.getReason());

        return getByExecutionId(assignment.getExecutionId());
    }

    // ==================== 查询 ====================

    @Override
    public AssignmentViewVO getByExecutionId(Long executionId) {
        if (executionId == null) {
            throw new BusinessException("缺少工序执行ID");
        }
        ProductionOperationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) {
            throw new BusinessException("工序执行记录不存在: " + executionId);
        }
        BigDecimal planned = exec.getInputQuantity() != null ? exec.getInputQuantity() : BigDecimal.ZERO;

        List<ProductionExecutionAssignment> rows = assignmentMapper.selectList(
                Wrappers.<ProductionExecutionAssignment>lambdaQuery()
                        .eq(ProductionExecutionAssignment::getExecutionId, executionId)
                        .orderByAsc(ProductionExecutionAssignment::getAssignmentId));

        AssignmentViewVO vo = new AssignmentViewVO();
        vo.setExecutionId(executionId);
        vo.setPlannedQuantity(planned);

        BigDecimal assigned = BigDecimal.ZERO;
        BigDecimal reportedTotal = BigDecimal.ZERO;
        List<AssignmentViewVO.AssignmentLineVO> lines = new ArrayList<>();
        for (ProductionExecutionAssignment row : rows) {
            if (!AssignmentStatusEnum.CANCELLED.getCode().equals(row.getAssignmentStatus())) {
                assigned = assigned.add(effectiveQuantity(row));
            }
            reportedTotal = reportedTotal.add(reportedQuantity(row.getAssignmentId()));

            AssignmentViewVO.AssignmentLineVO line = toLine(row);
            lines.add(line);
        }
        vo.setAssignedQuantity(assigned);
        vo.setReportedQuantity(reportedTotal);
        BigDecimal unassigned = planned.subtract(assigned);
        vo.setUnassignedQuantity(unassigned.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : unassigned);
        vo.setAssignments(lines);
        return vo;
    }

    @Override
    public boolean hasActiveAssignment(Long executionId) {
        if (executionId == null) return false;
        Long cnt = assignmentMapper.selectCount(
                Wrappers.<ProductionExecutionAssignment>lambdaQuery()
                        .eq(ProductionExecutionAssignment::getExecutionId, executionId)
                        .eq(ProductionExecutionAssignment::getAssignmentStatus, AssignmentStatusEnum.ACTIVE.getCode()));
        return cnt != null && cnt > 0;
    }

    // ==================== 工具 ====================

    /** 锁 Execution 行（SELECT ... FOR UPDATE），并发分配串行化 */
    private ProductionOperationExecution lockExecution(Long executionId) {
        return executionMapper.selectOne(
                Wrappers.<ProductionOperationExecution>lambdaQuery()
                        .eq(ProductionOperationExecution::getExecutionId, executionId)
                        .last("FOR UPDATE"));
    }

    /** 校验存在 ACTIVE DispatchNode 并返回（分配/释放前置） */
    private ProductionDispatchNode requireActiveNode(ProductionOperationExecution exec) {
        ProductionDispatchNode active = nodeMapper.selectOne(
                Wrappers.<ProductionDispatchNode>lambdaQuery()
                        .eq(ProductionDispatchNode::getDispatchId,
                                dispatchIdOf(exec.getExecutionId()))
                        .eq(ProductionDispatchNode::getNodeStatus, "ACTIVE")
                        .last("LIMIT 1"));
        if (active == null) {
            throw new BusinessException("该工序尚无 ACTIVE 责任节点，请先派工/明确责任人");
        }
        return active;
    }

    private Long dispatchIdOf(Long executionId) {
        try {
            List<Long> ids = jdbcTemplate.query(
                    "SELECT dispatch_id FROM production_dispatch WHERE execution_id = ? LIMIT 1",
                    (rs, i) -> rs.getLong("dispatch_id"), executionId);
            if (ids != null && !ids.isEmpty()) {
                return ids.get(0);
            }
        } catch (Exception e) {
            log.warn("反查 dispatchId 失败 execution={}: {}", executionId, e.getMessage());
        }
        return null;
    }

    /** 分配权限：超管放行；否则必须 assignment:add 权限 且 是当前 ACTIVE 责任人 */
    private void checkAssignRight(ProductionDispatchNode activeNode, Long operatorId) {
        if (SecurityUtils.hasPermission("*:*:*")) return;
        if (!SecurityUtils.hasPermission("production:assignment:add")) {
            throw new BusinessException("无作业分配权限");
        }
        if (operatorId == null || !operatorId.equals(activeNode.getAssigneeId())) {
            throw new BusinessException("只有当前 ACTIVE 责任人可以分配作业（当前责任人: "
                    + activeNode.getAssigneeName() + "）");
        }
    }

    private String resolveAssigneeName(Long userId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT nick_name FROM sys_user WHERE user_id = ?",
                    (rs, i) -> rs.getString("nick_name"), userId);
            if (names != null && !names.isEmpty() && names.get(0) != null && !names.get(0).isBlank()) {
                return names.get(0);
            }
        } catch (Exception e) {
            log.warn("查询执行人姓名失败 userId={}: {}", userId, e.getMessage());
        }
        return "用户" + userId;
    }

    /** SUM 有效（非 CANCELLED）分配的 effectiveQuantity */
    private BigDecimal sumEffectiveByExecution(Long executionId) {
        List<ProductionExecutionAssignment> rows = assignmentMapper.selectList(
                Wrappers.<ProductionExecutionAssignment>lambdaQuery()
                        .eq(ProductionExecutionAssignment::getExecutionId, executionId)
                        .ne(ProductionExecutionAssignment::getAssignmentStatus, AssignmentStatusEnum.CANCELLED.getCode()));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProductionExecutionAssignment r : rows) {
            sum = sum.add(effectiveQuantity(r));
        }
        return sum;
    }

    private BigDecimal effectiveQuantity(ProductionExecutionAssignment a) {
        BigDecimal assigned = a.getAssignedQuantity() != null ? a.getAssignedQuantity() : BigDecimal.ZERO;
        BigDecimal released = a.getReleasedQuantity() != null ? a.getReleasedQuantity() : BigDecimal.ZERO;
        BigDecimal eff = assigned.subtract(released);
        return eff.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : eff;
    }

    /** 某 assignment 的有效 SUBMITTED WorkReport qualified+defective 汇总 */
    private BigDecimal reportedQuantity(Long assignmentId) {
        List<ProductionWorkReport> reports = workReportMapper.selectList(
                Wrappers.<ProductionWorkReport>lambdaQuery()
                        .eq(ProductionWorkReport::getAssignmentId, assignmentId)
                        .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode()));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProductionWorkReport r : reports) {
            BigDecimal q = r.getQualifiedQuantity() != null ? r.getQualifiedQuantity() : BigDecimal.ZERO;
            BigDecimal d = r.getDefectiveQuantity() != null ? r.getDefectiveQuantity() : BigDecimal.ZERO;
            sum = sum.add(q).add(d);
        }
        return sum;
    }

    private AssignmentViewVO.AssignmentLineVO toLine(ProductionExecutionAssignment a) {
        AssignmentViewVO.AssignmentLineVO line = new AssignmentViewVO.AssignmentLineVO();
        line.setAssignmentId(a.getAssignmentId());
        line.setExecutionId(a.getExecutionId());
        line.setOrderId(a.getOrderId());
        line.setDispatchId(a.getDispatchId());
        line.setDispatchNodeId(a.getDispatchNodeId());
        line.setAssigneeId(a.getAssigneeId());
        line.setAssigneeName(a.getAssigneeName());
        line.setAssignedQuantity(a.getAssignedQuantity());
        line.setReleasedQuantity(a.getReleasedQuantity() != null ? a.getReleasedQuantity() : BigDecimal.ZERO);
        BigDecimal eff = effectiveQuantity(a);
        line.setEffectiveQuantity(eff);
        BigDecimal reported = reportedQuantity(a.getAssignmentId());
        line.setReportedQuantity(reported);
        BigDecimal remaining = eff.subtract(reported);
        line.setRemainingQuantity(remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining);
        line.setAssignmentStatus(a.getAssignmentStatus());

        // 派生状态：CANCELLED 行保持 CANCELLED；否则 remaining==0 → COMPLETED
        String derived;
        if (AssignmentStatusEnum.CANCELLED.getCode().equals(a.getAssignmentStatus())) {
            derived = AssignmentStatusEnum.CANCELLED.getCode();
        } else {
            derived = remaining.compareTo(BigDecimal.ZERO) <= 0
                    ? AssignmentStatusEnum.COMPLETED.getCode()
                    : AssignmentStatusEnum.ACTIVE.getCode();
        }
        line.setDerivedStatus(derived);
        line.setDerivedStatusLabel(AssignmentStatusEnum.labelOf(derived));
        line.setAssignedBy(a.getAssignedBy());
        line.setAssignedByName(a.getAssignedByName());
        line.setAssignedAt(a.getAssignedAt());
        line.setCancelledAt(a.getCancelledAt());
        line.setCancelReason(a.getCancelReason());
        return line;
    }
}
