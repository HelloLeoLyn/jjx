package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.MyTaskNodeVO;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskNodeVO;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.TaskNodeStatusEnum;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskNodeMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.TaskNodeService;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 生产任务树服务实现（P1 Task Tree Core + P2 收回/退回/报工接入）
 * <p>
 * 数量公式（统一定义，避免重复扣减）：
 *   effective = taskQuantity - recalledQuantity
 *   childOccupied = Σ 直接子节点 effective（已取消节点 effective=0，自然不占用）
 *   selfReported = Σ 当前 taskNode 有效 SUBMITTED WorkReport 的 qualified+defective（动态汇总，不落 TaskNode）
 *   selfRemaining = effective - childOccupied - selfReported（下限 0）
 *   availableToAssign = selfRemaining
 * <p>
 * 完成量不落 TaskNode：禁止报工回写 completedQuantity，统一从 WorkReport 动态汇总；
 * 撤销报工后 selfReported/selfRemaining 自动恢复。
 * <p>
 * 状态为动态投影（不落库）：
 *   CANCELLED  = effective 为 0 且无有效报工
 *   COMPLETED  = selfRemaining 为 0 且子树任务全部闭环
 *   ACTIVE     = 其他情况
 * <p>
 * 权限：assign 要求父节点持有人或超管/task:admin；recall 要求直接子节点的父节点持有人或超管/task:admin；
 * return 要求节点本人；Controller 注解兜底权限点（production:task:view/assign/recall/return/admin）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNodeServiceImpl implements TaskNodeService {

    private final ProductionTaskNodeMapper taskNodeMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final SysUserMapper sysUserMapper;
    private final ProductionWorkReportMapper workReportMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SysDeptMapper sysDeptMapper;

    // ==================== 根节点 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionTaskNode ensureRoot(Long executionId) {
        if (executionId == null) throw new BusinessException("缺少工序执行ID");
        ProductionTaskNode existing = taskNodeMapper.selectOne(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getExecutionId, executionId)
                .isNull(ProductionTaskNode::getParentNodeId)
                .last("LIMIT 1"));
        if (existing != null) return existing;

        ProductionOperationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) throw new BusinessException("工序执行记录不存在: " + executionId);

        ProductionTaskNode root = new ProductionTaskNode();
        root.setExecutionId(executionId);
        root.setParentNodeId(null);
        if (exec.getOperatorId() != null) {
            root.setAssigneeId(exec.getOperatorId());
            root.setAssigneeName(exec.getOperatorName());
        } else {
            root.setAssigneeId(SecurityUtils.getUserId());
            root.setAssigneeName(SecurityUtils.getUsername());
        }
        root.setTaskQuantity(exec.getInputQuantity() != null ? exec.getInputQuantity() : BigDecimal.ZERO);
        root.setRecalledQuantity(BigDecimal.ZERO);
        root.setCreateBy(SecurityUtils.getUsername());
        taskNodeMapper.insert(root);
        log.info("任务树根节点建立 executionId={}, rootId={}, taskQuantity={}",
                executionId, root.getTaskNodeId(), root.getTaskQuantity());
        return root;
    }

    // ==================== 任务树查询 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskNodeVO getTaskTree(Long executionId) {
        if (executionId == null) throw new BusinessException("缺少工序执行ID");
        ensureRoot(executionId); // 第一次需要任务树时建立根节点
        List<ProductionTaskNode> nodes = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getExecutionId, executionId)
                .orderByAsc(ProductionTaskNode::getParentNodeId)
                .orderByAsc(ProductionTaskNode::getTaskNodeId));
        // 一次加载本工序全部有效报工，按 taskNodeId 聚合 selfReported，避免逐节点查询
        Map<Long, BigDecimal> selfReportedByNode = loadSelfReportedByExecution(executionId);
        // 内存聚合 childOccupied 与父子结构，公式与剩余计算一致，避免树构建 N+1
        Map<Long, BigDecimal> childOccupiedByParent = new HashMap<>();
        Map<Long, List<ProductionTaskNode>> childrenByParent = new HashMap<>();
        for (ProductionTaskNode n : nodes) {
            if (n.getParentNodeId() != null) {
                childOccupiedByParent.merge(n.getParentNodeId(), effectiveOf(n), BigDecimal::add);
                childrenByParent.computeIfAbsent(n.getParentNodeId(), k -> new ArrayList<>()).add(n);
            }
        }
        Map<Long, TaskNodeVO> voMap = new HashMap<>();
        Map<Long, TaskNodeStatusEnum> projected = new HashMap<>();
        for (ProductionTaskNode n : nodes) {
            TaskNodeVO vo = TaskNodeVO.from(n);
            BigDecimal selfReported = selfReportedByNode.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO);
            BigDecimal remaining = effectiveOf(n)
                    .subtract(childOccupiedByParent.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO))
                    .subtract(selfReported);
            TaskNodeStatusEnum st = projectStatus(n, childrenByParent, selfReportedByNode,
                    childOccupiedByParent, projected);
            vo.setSelfReported(selfReported);
            vo.setChildOccupied(childOccupiedByParent.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO));
            vo.setRemainingQuantity(remaining);
            vo.setAvailableToAssign(floorZero(remaining));
            vo.setStatus(st.getCode());
            vo.setStatusLabel(st.getLabel());
            voMap.put(vo.getTaskNodeId(), vo);
        }
        for (TaskNodeVO vo : voMap.values()) {
            if (vo.getParentNodeId() == null) {
                continue;
            }
            TaskNodeVO parent = voMap.get(vo.getParentNodeId());
            if (parent != null) parent.getChildren().add(vo);
        }
        TaskNodeVO root = null;
        for (TaskNodeVO vo : voMap.values()) {
            if (vo.getParentNodeId() == null) {
                root = vo;
                break;
            }
        }
        if (root == null) {
            throw new BusinessException("工序任务树不存在: " + executionId);
        }
        return root;
    }

    // ==================== 分配（创建子节点） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProductionTaskNode> assignChildren(Long parentNodeId, List<TaskAssignItemDTO> items) {
        if (parentNodeId == null) throw new BusinessException("缺少父节点ID");
        if (items == null || items.isEmpty()) throw new BusinessException("分配明细不能为空");

        // 行锁父节点，串行化同一父节点的并发分配，避免重复扣减超分
        ProductionTaskNode parent = taskNodeMapper.selectOne(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getTaskNodeId, parentNodeId)
                .last("FOR UPDATE"));
        if (parent == null) throw new BusinessException("任务节点不存在: " + parentNodeId);

        // 权限：当前用户是节点持有人 或 超管/task:admin（production:task:assign 由 Controller 注解兜底）
        if (!isAdminOrTaskAdmin() && !SecurityUtils.getUserId().equals(parent.getAssigneeId())) {
            throw new BusinessException("只有当前任务节点持有人可以分配任务");
        }

        // 明细校验：数量>0、同批不重复、合计不超过可分配数量（状态投影为 CANCELLED/COMPLETED 时
        // availableToAssign 自然为 0，分配会被数量校验拒绝，无需单独状态判断）
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (TaskAssignItemDTO it : items) {
            if (it.getUserId() == null) throw new BusinessException("分配人不能为空");
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("分配数量必须大于 0");
            }
            if (!seen.add(it.getUserId())) {
                throw new BusinessException("同一批分配中用户重复: " + it.getUserId());
            }
            total = total.add(it.getQuantity());
        }
        BigDecimal available = availableToAssignOf(parent);
        if (total.compareTo(available) > 0) {
            throw new BusinessException("分配数量合计 " + strip(total)
                    + " 超过节点可分配数量 " + strip(available));
        }

        // 创建子节点
        List<ProductionTaskNode> created = new ArrayList<>();
        for (TaskAssignItemDTO it : items) {
            ProductionTaskNode child = new ProductionTaskNode();
            child.setExecutionId(parent.getExecutionId());
            child.setParentNodeId(parent.getTaskNodeId());
            child.setAssigneeId(it.getUserId());
            child.setAssigneeName(assigneeNameOf(it.getUserId()));
            child.setTaskQuantity(it.getQuantity());
            child.setRecalledQuantity(BigDecimal.ZERO);
            child.setCreateBy(SecurityUtils.getUsername());
            taskNodeMapper.insert(child);
            created.add(child);
        }
        log.info("任务分配 parentNodeId={}, 子节点数={}, 合计={}", parentNodeId, created.size(), strip(total));
        return created;
    }

    // ==================== 收回（P2） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionTaskNode recall(Long childNodeId, BigDecimal quantity) {
        if (childNodeId == null) throw new BusinessException("缺少子节点ID");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("收回数量必须大于 0");
        }
        ProductionTaskNode child = taskNodeMapper.selectOne(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getTaskNodeId, childNodeId)
                .last("FOR UPDATE"));
        if (child == null) throw new BusinessException("任务节点不存在: " + childNodeId);
        if (child.getParentNodeId() == null) throw new BusinessException("根节点不可收回，只能收回直接子节点");

        // 权限：当前用户必须是 child 的父节点持有人（超管/task:admin 放行；recall 权限点由 Controller 注解兜底）
        ProductionTaskNode parent = requireNode(child.getParentNodeId());
        if (!isAdminOrTaskAdmin() && !SecurityUtils.getUserId().equals(parent.getAssigneeId())) {
            throw new BusinessException("只有父节点持有人可以收回子节点任务");
        }

        // 可收回量 = child.selfRemaining：已完成（selfReported）与已下分（childOccupied）天然不可收回
        BigDecimal selfRemaining = floorZero(remainingOf(child));
        if (quantity.compareTo(selfRemaining) > 0) {
            throw new BusinessException("收回数量 " + strip(quantity)
                    + " 超过子节点可收回数量 " + strip(selfRemaining));
        }

        child.setRecalledQuantity(nvl(child.getRecalledQuantity()).add(quantity));
        child.setUpdateBy(SecurityUtils.getUsername());
        taskNodeMapper.updateById(child);
        log.info("收回子节点任务 childNodeId={}, 收回数量={}, 当前已收回={}",
                childNodeId, strip(quantity), strip(child.getRecalledQuantity()));
        return child;
    }

    // ==================== 退回（P2） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionTaskNode returnNode(Long nodeId, BigDecimal quantity) {
        if (nodeId == null) throw new BusinessException("缺少任务节点ID");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退回数量必须大于 0");
        }
        ProductionTaskNode node = taskNodeMapper.selectOne(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getTaskNodeId, nodeId)
                .last("FOR UPDATE"));
        if (node == null) throw new BusinessException("任务节点不存在: " + nodeId);
        if (node.getParentNodeId() == null) throw new BusinessException("根节点不允许退回");

        // 权限：只有节点本人可退回（return 权限点由 Controller 注解兜底）
        if (!SecurityUtils.getUserId().equals(node.getAssigneeId())) {
            throw new BusinessException("只有任务节点持有人本人可以退回任务");
        }

        // 只能退回自己的 selfRemaining（已报工/已下分部分不可退回）
        BigDecimal selfRemaining = floorZero(remainingOf(node));
        if (quantity.compareTo(selfRemaining) > 0) {
            throw new BusinessException("退回数量 " + strip(quantity)
                    + " 超过节点可退回数量 " + strip(selfRemaining));
        }

        node.setRecalledQuantity(nvl(node.getRecalledQuantity()).add(quantity));
        node.setUpdateBy(SecurityUtils.getUsername());
        taskNodeMapper.updateById(node);
        log.info("退回任务 nodeId={}, 退回数量={}, 当前已退回={}",
                nodeId, strip(quantity), strip(node.getRecalledQuantity()));
        return node;
    }

    // ==================== 数量计算 ====================

    @Override
    public ProductionTaskNode getNode(Long taskNodeId) {
        return requireNode(taskNodeId);
    }

    @Override
    public BigDecimal remaining(Long taskNodeId) {
        return remainingOf(requireNode(taskNodeId));
    }

    @Override
    public BigDecimal availableToAssign(Long taskNodeId) {
        return floorZero(remaining(taskNodeId));
    }

    // ==================== 我的任务（P3） ====================

    @Override
    @Transactional(readOnly = true)
    public List<MyTaskNodeVO> myTaskNodes() {
        Long userId = SecurityUtils.getUserId();
        List<ProductionTaskNode> nodes = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getAssigneeId, userId)
                .orderByDesc(ProductionTaskNode::getTaskNodeId));
        if (nodes.isEmpty()) return new ArrayList<>();

        // 批量加载 execution + 工单号，避免逐节点查询
        Map<Long, ProductionOperationExecution> execMap = new HashMap<>();
        Set<Long> execIds = nodes.stream().map(ProductionTaskNode::getExecutionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!execIds.isEmpty()) {
            for (ProductionOperationExecution e : executionMapper.selectBatchIds(execIds)) {
                execMap.put(e.getExecutionId(), e);
            }
        }
        Map<Long, String> orderNoMap = loadOrderNos(execMap.values().stream()
                .map(ProductionOperationExecution::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<MyTaskNodeVO> vos = new ArrayList<>();
        for (ProductionTaskNode n : nodes) {
            MyTaskNodeVO vo = new MyTaskNodeVO();
            vo.setTaskNodeId(n.getTaskNodeId());
            vo.setExecutionId(n.getExecutionId());
            vo.setParentNodeId(n.getParentNodeId());
            vo.setAssigneeId(n.getAssigneeId());
            vo.setAssigneeName(n.getAssigneeName());
            vo.setTaskQuantity(n.getTaskQuantity());
            vo.setRecalledQuantity(n.getRecalledQuantity());
            BigDecimal childOccupied = childOccupiedOf(n.getTaskNodeId());
            BigDecimal selfReported = selfReportedOf(n);
            BigDecimal remaining = floorZero(effectiveOf(n).subtract(childOccupied).subtract(selfReported));
            vo.setSelfReported(selfReported);
            vo.setChildOccupied(childOccupied);
            vo.setSelfRemaining(remaining);
            vo.setAvailableToAssign(remaining);
            TaskNodeStatusEnum st = projectStatusOf(n, selfReported, remaining);
            vo.setStatus(st.getCode());
            vo.setStatusLabel(st.getLabel());
            ProductionOperationExecution e = execMap.get(n.getExecutionId());
            if (e != null) {
                vo.setOrderId(e.getOrderId());
                vo.setOrderNo(orderNoMap.get(e.getOrderId()));
                vo.setProcessName(e.getProcessName());
                vo.setProcessOrder(e.getProcessOrder());
                vo.setExecutionStatus(e.getExecutionStatus());
                ExecutionStatusEnum stEnum = ExecutionStatusEnum.getByCode(e.getExecutionStatus());
                vo.setExecutionStatusDesc(stEnum == null ? null : stEnum.getName());
                vo.setExecutionInputQuantity(e.getInputQuantity());
            }
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 分配候选人员（P3） ====================

    @Override
    @Transactional(readOnly = true)
    public List<TaskCandidateVO> candidates() {
        Long userId = SecurityUtils.getUserId();
        SysUser me = sysUserMapper.selectById(userId);
        Long deptId = me == null ? null : me.getDeptId();
        if (deptId == null) return new ArrayList<>(); // 无部门 → 空候选，不伪造全公司

        // 部门子树 = 当前用户允许分配的人员组织范围（组织/上下级的最小可靠投影）
        List<SysDept> allDepts = sysDeptMapper.selectList(null);
        Map<Long, List<SysDept>> childrenByParent = new HashMap<>();
        for (SysDept d : allDepts) {
            childrenByParent.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
        }
        Set<Long> deptIds = new HashSet<>();
        collectDeptSubtree(deptId, childrenByParent, deptIds);
        Map<Long, String> deptNameMap = new HashMap<>();
        for (SysDept d : allDepts) deptNameMap.put(d.getId(), d.getDeptName());

        List<SysUser> users = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getDeptId, deptIds)
                .ne(SysUser::getUserId, userId));
        List<TaskCandidateVO> out = new ArrayList<>();
        for (SysUser u : users) {
            TaskCandidateVO c = new TaskCandidateVO();
            c.setUserId(u.getUserId());
            c.setUserName(u.getUserName());
            c.setNickName(u.getNickName());
            c.setDeptId(u.getDeptId());
            c.setDeptName(deptNameMap.get(u.getDeptId()));
            out.add(c);
        }
        return out;
    }

    /** effective = taskQuantity - recalledQuantity */
    private BigDecimal effectiveOf(ProductionTaskNode node) {
        return nvl(node.getTaskQuantity()).subtract(nvl(node.getRecalledQuantity()));
    }

    /** childOccupied = Σ 直接子节点 effective（已取消节点 effective=0，自然不占用） */
    private BigDecimal childOccupiedOf(Long parentNodeId) {
        if (parentNodeId == null) return BigDecimal.ZERO;
        List<ProductionTaskNode> children = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getParentNodeId, parentNodeId));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProductionTaskNode c : children) {
            if (parentNodeId.equals(c.getParentNodeId())) {
                sum = sum.add(effectiveOf(c));
            }
        }
        return sum;
    }

    /** selfReported = Σ 当前 taskNode 有效 SUBMITTED WorkReport 的 qualified+defective（动态汇总，撤销自动恢复） */
    private BigDecimal selfReportedOf(ProductionTaskNode node) {
        if (node == null || node.getTaskNodeId() == null) return BigDecimal.ZERO;
        List<ProductionWorkReport> reports = workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                .eq(ProductionWorkReport::getTaskNodeId, node.getTaskNodeId())
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode()));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProductionWorkReport r : reports) {
            if (node.getTaskNodeId().equals(r.getTaskNodeId())
                    && WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())) {
                sum = sum.add(nvl(r.getQualifiedQuantity())).add(nvl(r.getDefectiveQuantity()));
            }
        }
        return sum;
    }

    /** 批量查询工单号（冗余快照，失败仅告警不影响主流程） */
    private Map<Long, String> loadOrderNos(Set<Long> orderIds) {
        Map<Long, String> map = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) return map;
        String in = orderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            jdbcTemplate.query("SELECT order_id, order_no FROM production_order WHERE order_id IN (" + in + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            map.put(rs.getLong("order_id"), rs.getString("order_no")));
        } catch (Exception e) {
            log.warn("查询工单号失败: {}", e.getMessage());
        }
        return map;
    }

    private void collectDeptSubtree(Long deptId, Map<Long, List<SysDept>> childrenByParent, Set<Long> acc) {
        if (deptId == null || !acc.add(deptId)) return;
        for (SysDept d : childrenByParent.getOrDefault(deptId, List.of())) {
            collectDeptSubtree(d.getId(), childrenByParent, acc);
        }
    }

    /** 单节点场景的状态投影（无子树缓存；selfRemaining=0 即视为本人部分闭环） */
    private TaskNodeStatusEnum projectStatusOf(ProductionTaskNode node, BigDecimal selfReported, BigDecimal remaining) {
        if (effectiveOf(node).compareTo(BigDecimal.ZERO) == 0 && selfReported.compareTo(BigDecimal.ZERO) == 0) {
            return TaskNodeStatusEnum.CANCELLED;
        }
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            return TaskNodeStatusEnum.COMPLETED;
        }
        return TaskNodeStatusEnum.ACTIVE;
    }

    /** 一次加载某工序全部有效报工，按 taskNodeId 聚合 selfReported（树查询避免逐节点 N+1） */
    private Map<Long, BigDecimal> loadSelfReportedByExecution(Long executionId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        List<ProductionWorkReport> reports = workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                .eq(ProductionWorkReport::getExecutionId, executionId)
                .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode()));
        for (ProductionWorkReport r : reports) {
            if (r.getTaskNodeId() == null
                    || !WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())) {
                continue;
            }
            map.merge(r.getTaskNodeId(),
                    nvl(r.getQualifiedQuantity()).add(nvl(r.getDefectiveQuantity())), BigDecimal::add);
        }
        return map;
    }

    /** remaining = effective - childOccupied - selfReported */
    private BigDecimal remainingOf(ProductionTaskNode node) {
        return effectiveOf(node)
                .subtract(childOccupiedOf(node.getTaskNodeId()))
                .subtract(selfReportedOf(node));
    }

    /** 父节点已加载场景下的可分配数量（避免重复查询） */
    private BigDecimal availableToAssignOf(ProductionTaskNode parent) {
        return floorZero(remainingOf(parent));
    }

    // ==================== 状态动态投影 ====================

    /**
     * 动态状态投影（不落库，避免第二事实源）：
     * CANCELLED = effective 为 0 且无有效报工；
     * COMPLETED = selfRemaining 为 0 且子树任务全部闭环；
     * 其余 ACTIVE。
     */
    private TaskNodeStatusEnum projectStatus(ProductionTaskNode node,
                                             Map<Long, List<ProductionTaskNode>> childrenByParent,
                                             Map<Long, BigDecimal> selfReportedByNode,
                                             Map<Long, BigDecimal> childOccupiedByParent,
                                             Map<Long, TaskNodeStatusEnum> memo) {
        TaskNodeStatusEnum cached = memo.get(node.getTaskNodeId());
        if (cached != null) return cached;
        BigDecimal effective = effectiveOf(node);
        BigDecimal selfReported = selfReportedByNode.getOrDefault(node.getTaskNodeId(), BigDecimal.ZERO);
        if (effective.compareTo(BigDecimal.ZERO) == 0 && selfReported.compareTo(BigDecimal.ZERO) == 0) {
            memo.put(node.getTaskNodeId(), TaskNodeStatusEnum.CANCELLED);
            return TaskNodeStatusEnum.CANCELLED;
        }
        BigDecimal remaining = effective
                .subtract(childOccupiedByParent.getOrDefault(node.getTaskNodeId(), BigDecimal.ZERO))
                .subtract(selfReported);
        if (remaining.compareTo(BigDecimal.ZERO) == 0
                && subtreeClosed(node, childrenByParent, selfReportedByNode, childOccupiedByParent, memo)) {
            memo.put(node.getTaskNodeId(), TaskNodeStatusEnum.COMPLETED);
            return TaskNodeStatusEnum.COMPLETED;
        }
        memo.put(node.getTaskNodeId(), TaskNodeStatusEnum.ACTIVE);
        return TaskNodeStatusEnum.ACTIVE;
    }

    /** 子树任务是否全部闭环（直接子节点均为 COMPLETED/CANCELLED；无子节点视为闭环） */
    private boolean subtreeClosed(ProductionTaskNode node,
                                  Map<Long, List<ProductionTaskNode>> childrenByParent,
                                  Map<Long, BigDecimal> selfReportedByNode,
                                  Map<Long, BigDecimal> childOccupiedByParent,
                                  Map<Long, TaskNodeStatusEnum> memo) {
        List<ProductionTaskNode> children = childrenByParent.getOrDefault(node.getTaskNodeId(), List.of());
        for (ProductionTaskNode c : children) {
            TaskNodeStatusEnum s = projectStatus(c, childrenByParent, selfReportedByNode,
                    childOccupiedByParent, memo);
            if (s != TaskNodeStatusEnum.COMPLETED && s != TaskNodeStatusEnum.CANCELLED) {
                return false;
            }
        }
        return true;
    }

    // ==================== helpers ====================

    private boolean isAdminOrTaskAdmin() {
        return SecurityUtils.hasPermission("*:*:*") || SecurityUtils.hasPermission("production:task:admin");
    }

    private ProductionTaskNode requireNode(Long taskNodeId) {
        ProductionTaskNode node = taskNodeMapper.selectById(taskNodeId);
        if (node == null) throw new BusinessException("任务节点不存在: " + taskNodeId);
        return node;
    }

    private String assigneeNameOf(Long userId) {
        if (userId == null) return null;
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null) return null;
            return u.getNickName() != null && !u.getNickName().isBlank() ? u.getNickName() : u.getUserName();
        } catch (Exception e) {
            log.warn("查询分配人姓名失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private BigDecimal floorZero(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String strip(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
