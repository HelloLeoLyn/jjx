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
import com.jjx.production.domain.vo.TaskTreeEventVO;
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
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
        // 系统根：不绑定任何业务人员（assigneeId/Name 恒为 NULL）
        // - 不进入任务链、我的任务、报工（myTaskNodes 按 assigneeId 查询、报工要求 operatorId=assigneeId，天然排除）
        // - 无真实人员子节点时页面投影为"未分配"
        root.setAssigneeId(null);
        root.setAssigneeName(null);
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
        // 查询视角（与动作权限解耦）：全局角色看完整树；普通角色只能查看本人持有节点的子树，
        // 且必须在 ensureRoot（写副作用）之前校验关联，避免无关查看为其他工序建立系统根。
        boolean globalScope = SecurityUtils.isGlobalProductionScope();
        Long currentUserId = null;
        if (!globalScope) {
            currentUserId = SecurityUtils.getUserId();
            Long cnt = taskNodeMapper.selectCount(Wrappers.<ProductionTaskNode>lambdaQuery()
                    .eq(ProductionTaskNode::getExecutionId, executionId)
                    .eq(ProductionTaskNode::getAssigneeId, currentUserId));
            if (cnt == null || cnt.longValue() == 0) {
                throw new BusinessException("当前用户与该工序任务树无关联");
            }
        }
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
        // 补全上级持有人姓名：节点详情“任务来源”（普通用户子树视图下上级节点不在树内，必须由服务端透出）
        for (TaskNodeVO vo : voMap.values()) {
            if (vo.getParentNodeId() == null) {
                continue;
            }
            TaskNodeVO parent = voMap.get(vo.getParentNodeId());
            vo.setParentAssigneeName(parent == null ? null : parent.getAssigneeName());
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
        if (globalScope) {
            return root;
        }
        return buildPersonalTree(voMap, root, currentUserId);
    }

    /**
     * 普通用户任务树视图：以本人持有的最上层 TaskNode 为业务根，只返回本人下级子树。
     * 上级信息（parentNodeId/parentAssigneeName）保留在节点详情，不扩大可操作范围。
     * 同一用户在同一 Execution 持有多个节点时全部返回（按 executionId + assigneeId 聚合展示）。
     */
    private TaskNodeVO buildPersonalTree(Map<Long, TaskNodeVO> voMap, TaskNodeVO root, Long userId) {
        List<TaskNodeVO> topHeld = new ArrayList<>();
        for (TaskNodeVO vo : voMap.values()) {
            if (vo.getAssigneeId() == null || !userId.equals(vo.getAssigneeId())) {
                continue;
            }
            // 跳过嵌套在本人其他节点下的节点（其已在上级本人节点的子树中，避免重复展示）
            Long pid = vo.getParentNodeId();
            boolean underOwn = false;
            while (pid != null) {
                TaskNodeVO p = voMap.get(pid);
                if (p == null) break;
                if (userId.equals(p.getAssigneeId())) {
                    underOwn = true;
                    break;
                }
                pid = p.getParentNodeId();
            }
            if (!underOwn) {
                topHeld.add(vo);
            }
        }
        if (topHeld.isEmpty()) {
            throw new BusinessException("当前用户与该工序任务树无关联");
        }
        TaskNodeVO virtualRoot = new TaskNodeVO();
        virtualRoot.setExecutionId(root.getExecutionId());
        virtualRoot.setAssigneeId(null);
        virtualRoot.setAssigneeName(null);
        virtualRoot.setChildren(topHeld);
        return virtualRoot;
    }

    // ==================== 任务树懒加载（派工管理主列表树形视图） ====================

    @Override
    @Transactional(readOnly = true)
    public List<TaskNodeVO> listChildren(Long executionId, Long parentNodeId) {
        if (executionId == null) throw new BusinessException("缺少工序执行ID");
        boolean globalScope = SecurityUtils.isGlobalProductionScope();
        Long currentUserId = globalScope ? null : SecurityUtils.getUserId();

        if (parentNodeId == null) {
            // 当前视角第一层：纯浏览不建根（不 ensureRoot，避免查看触发写库）
            if (globalScope) {
                ProductionTaskNode root = findRootNode(executionId);
                if (root == null) return List.of(); // 未建立任务树 → 未分配
                return loadChildrenProjection(executionId, root.getTaskNodeId());
            }
            return loadPersonalTopLevel(executionId, currentUserId);
        }

        ProductionTaskNode parent = requireNode(parentNodeId);
        if (!executionId.equals(parent.getExecutionId())) {
            throw new BusinessException("任务节点不属于该工序");
        }
        if (!globalScope && !isWithinMyScope(parent, currentUserId)) {
            throw new BusinessException("无权查看该任务分支");
        }
        return loadChildrenProjection(executionId, parentNodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskNodeVO getNodeDetail(Long taskNodeId) {
        ProductionTaskNode node = requireNode(taskNodeId);
        boolean globalScope = SecurityUtils.isGlobalProductionScope();
        if (!globalScope && !isWithinMyScope(node, SecurityUtils.getUserId())) {
            throw new BusinessException("无权查看该任务节点");
        }
        BigDecimal effective = effectiveOf(node);
        BigDecimal selfReported = selfReportedOf(node);
        List<ProductionTaskNode> children = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getParentNodeId, taskNodeId));
        BigDecimal occupied = BigDecimal.ZERO;
        for (ProductionTaskNode c : children) {
            occupied = occupied.add(effectiveOf(c));
        }
        boolean hasChildren = !children.isEmpty();
        BigDecimal remaining = effective.subtract(occupied).subtract(selfReported);
        TaskNodeVO vo = TaskNodeVO.from(node);
        vo.setSelfReported(selfReported);
        vo.setChildOccupied(occupied);
        vo.setRemainingQuantity(remaining);
        vo.setAvailableToAssign(floorZero(remaining));
        vo.setHasChildren(hasChildren);
        TaskNodeStatusEnum st = lazyStatus(effective, selfReported, remaining, hasChildren);
        vo.setStatus(st.getCode());
        vo.setStatusLabel(st.getLabel());
        if (node.getParentNodeId() != null) {
            ProductionTaskNode parent = taskNodeMapper.selectById(node.getParentNodeId());
            vo.setParentAssigneeName(parent == null ? null : parent.getAssigneeName());
        }
        return vo;
    }

    /** 系统根节点（parent_node_id IS NULL；不存在返回 null，浏览不建根） */
    private ProductionTaskNode findRootNode(Long executionId) {
        return taskNodeMapper.selectOne(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getExecutionId, executionId)
                .isNull(ProductionTaskNode::getParentNodeId)
                .last("LIMIT 1"));
    }

    /**
     * 普通用户视图范围：node 是本人持有节点，或本人持有节点的下级子树节点。
     * 从 node 向上沿父链爬取，任一祖先（含自身）assigneeId = 当前用户即放行；到系统根仍未命中则拒绝。
     */
    private boolean isWithinMyScope(ProductionTaskNode node, Long userId) {
        if (userId == null || node == null) return false;
        ProductionTaskNode cur = node;
        Set<Long> visited = new HashSet<>();
        while (cur != null && visited.add(cur.getTaskNodeId())) {
            if (userId.equals(cur.getAssigneeId())) return true;
            if (cur.getParentNodeId() == null) break;
            cur = taskNodeMapper.selectById(cur.getParentNodeId());
        }
        return false;
    }

    /** 普通用户第一层：本人顶层持有节点（同一用户多节点全部返回；嵌套在本人其他节点下的跳过） */
    private List<TaskNodeVO> loadPersonalTopLevel(Long executionId, Long userId) {
        if (userId == null) return List.of();
        List<ProductionTaskNode> held = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getExecutionId, executionId)
                .eq(ProductionTaskNode::getAssigneeId, userId)
                .orderByAsc(ProductionTaskNode::getTaskNodeId));
        if (held.isEmpty()) return List.of();
        List<ProductionTaskNode> top = new ArrayList<>();
        for (ProductionTaskNode n : held) {
            Long pid = n.getParentNodeId();
            boolean underOwn = false;
            while (pid != null) {
                ProductionTaskNode p = taskNodeMapper.selectById(pid);
                if (p == null) break;
                if (userId.equals(p.getAssigneeId())) {
                    underOwn = true;
                    break;
                }
                pid = p.getParentNodeId();
            }
            if (!underOwn) top.add(n);
        }
        return loadNodesProjection(top);
    }

    /** 某节点的直接子节点投影（按 parent_node_id 查询，只加载一层） */
    private List<TaskNodeVO> loadChildrenProjection(Long executionId, Long parentNodeId) {
        List<ProductionTaskNode> children = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getExecutionId, executionId)
                .eq(ProductionTaskNode::getParentNodeId, parentNodeId)
                .orderByAsc(ProductionTaskNode::getTaskNodeId));
        return loadNodesProjection(children);
    }

    /** 批量节点投影：selfReported/childOccupied/remaining/availableToAssign/status/hasChildren/任务来源，一次只算一层 */
    private List<TaskNodeVO> loadNodesProjection(List<ProductionTaskNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return List.of();
        List<Long> ids = nodes.stream().map(ProductionTaskNode::getTaskNodeId).toList();
        // selfReported：本批节点有效报工（WorkReport 动态汇总，不落 TaskNode）
        Map<Long, BigDecimal> reported = loadSelfReportedByNodeIds(ids);
        // 下一层：childOccupied + hasChildren（只查一层子节点）
        Map<Long, BigDecimal> childOcc = new HashMap<>();
        Set<Long> hasChildIds = new HashSet<>();
        List<ProductionTaskNode> nextLevel = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .in(ProductionTaskNode::getParentNodeId, ids));
        for (ProductionTaskNode c : nextLevel) {
            hasChildIds.add(c.getParentNodeId());
            childOcc.merge(c.getParentNodeId(), effectiveOf(c), BigDecimal::add);
        }
        // 任务来源：上级持有人姓名（上级不在本层时仍可展示，不扩大操作范围）
        Map<Long, String> parentNameCache = new HashMap<>();
        List<TaskNodeVO> out = new ArrayList<>();
        for (ProductionTaskNode n : nodes) {
            TaskNodeVO vo = TaskNodeVO.from(n);
            BigDecimal effective = effectiveOf(n);
            BigDecimal selfReported = reported.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO);
            BigDecimal occupied = childOcc.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO);
            BigDecimal remaining = effective.subtract(occupied).subtract(selfReported);
            boolean hasChildren = hasChildIds.contains(n.getTaskNodeId());
            vo.setSelfReported(selfReported);
            vo.setChildOccupied(occupied);
            vo.setRemainingQuantity(remaining);
            vo.setAvailableToAssign(floorZero(remaining));
            vo.setHasChildren(hasChildren);
            TaskNodeStatusEnum st = lazyStatus(effective, selfReported, remaining, hasChildren);
            vo.setStatus(st.getCode());
            vo.setStatusLabel(st.getLabel());
            vo.setParentAssigneeName(parentAssigneeNameOf(n.getParentNodeId(), parentNameCache));
            out.add(vo);
        }
        return out;
    }

    /** 按 taskNodeId 批量汇总有效报工 */
    private Map<Long, BigDecimal> loadSelfReportedByNodeIds(java.util.Collection<Long> nodeIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (nodeIds == null || nodeIds.isEmpty()) return map;
        List<ProductionWorkReport> reports = workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                .in(ProductionWorkReport::getTaskNodeId, nodeIds)
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

    /** 上级持有人姓名（缓存，避免同父多子重复查询） */
    private String parentAssigneeNameOf(Long parentNodeId, Map<Long, String> cache) {
        if (parentNodeId == null) return null;
        return cache.computeIfAbsent(parentNodeId, id -> {
            ProductionTaskNode p = taskNodeMapper.selectById(id);
            return p == null ? null : p.getAssigneeName();
        });
    }

    /**
     * 懒加载单层状态投影（与整树 projectStatus 同一定义，但仅在已加载数据内判定）：
     * CANCELLED = effective 0 且无有效报工；COMPLETED = selfRemaining 0 且无未闭环子节点（懒加载下仅当无直接子节点可确认）；
     * 其余 ACTIVE。完整闭环判定仍以整树查询（getTaskTree）为准。
     */
    private TaskNodeStatusEnum lazyStatus(BigDecimal effective, BigDecimal selfReported,
                                          BigDecimal remaining, boolean hasChildren) {
        if (effective.compareTo(BigDecimal.ZERO) == 0 && selfReported.compareTo(BigDecimal.ZERO) == 0) {
            return TaskNodeStatusEnum.CANCELLED;
        }
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 && !hasChildren) {
            return TaskNodeStatusEnum.COMPLETED;
        }
        return TaskNodeStatusEnum.ACTIVE;
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
        // 系统根（assigneeId=NULL）无持有人，放行首次分配；真实人员节点的分配仍要求节点持有人
        if (!isAdminOrTaskAdmin()
                && parent.getAssigneeId() != null
                && !SecurityUtils.getUserId().equals(parent.getAssigneeId())) {
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
    public ProductionTaskNode lockNode(Long taskNodeId) {
        if (taskNodeId == null) throw new BusinessException("缺少任务节点ID");
        // 行锁（FOR UPDATE）：与 assignChildren/recall/returnNode 相同的锁顺序（先锁节点行），
        // 保证 WorkReport submit 的 selfRemaining 读取与写入在同一事务内被串行化（TT-FINAL-04）
        ProductionTaskNode node = taskNodeMapper.selectOne(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getTaskNodeId, taskNodeId)
                .last("FOR UPDATE"));
        if (node == null) throw new BusinessException("任务节点不存在: " + taskNodeId);
        return node;
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

        // TT-FINAL-05 H：状态投影使用真实子树闭环语义（selfRemaining=0 不等于本人完成，
        // 全部下分后剩余 0 必须保持 ACTIVE；只有子树全部闭环才投影为 COMPLETED）
        Map<Long, List<ProductionTaskNode>> childrenByParent = new HashMap<>();
        Map<Long, BigDecimal> childOccupiedByParent = new HashMap<>();
        Map<Long, BigDecimal> selfReportedByNode = new HashMap<>();
        Map<Long, TaskNodeStatusEnum> memo = new HashMap<>();
        Map<Long, ProductionTaskNode> nodeById = new HashMap<>();
        if (!execIds.isEmpty()) {
            List<ProductionTaskNode> allNodes = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                    .in(ProductionTaskNode::getExecutionId, execIds));
            for (ProductionTaskNode n : allNodes) {
                nodeById.put(n.getTaskNodeId(), n);
                if (n.getParentNodeId() != null) {
                    childrenByParent.computeIfAbsent(n.getParentNodeId(), k -> new ArrayList<>()).add(n);
                    childOccupiedByParent.merge(n.getParentNodeId(), effectiveOf(n), BigDecimal::add);
                }
            }
            selfReportedByNode.putAll(loadSelfReportedByExecutions(execIds));
        }

        List<MyTaskNodeVO> vos = new ArrayList<>();
        for (ProductionTaskNode n : nodes) {
            MyTaskNodeVO vo = new MyTaskNodeVO();
            vo.setTaskNodeId(n.getTaskNodeId());
            vo.setExecutionId(n.getExecutionId());
            vo.setParentNodeId(n.getParentNodeId());
            vo.setAssigneeId(n.getAssigneeId());
            vo.setAssigneeName(n.getAssigneeName());
            ProductionTaskNode parent = n.getParentNodeId() == null ? null : nodeById.get(n.getParentNodeId());
            vo.setParentAssigneeName(parent == null ? null : parent.getAssigneeName());
            vo.setTaskQuantity(n.getTaskQuantity());
            vo.setRecalledQuantity(n.getRecalledQuantity());
            BigDecimal childOccupied = childOccupiedByParent.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO);
            BigDecimal selfReported = selfReportedByNode.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO);
            BigDecimal remaining = floorZero(effectiveOf(n).subtract(childOccupied).subtract(selfReported));
            vo.setSelfReported(selfReported);
            vo.setChildOccupied(childOccupied);
            vo.setSelfRemaining(remaining);
            vo.setAvailableToAssign(remaining);
            TaskNodeStatusEnum st = projectStatus(n, childrenByParent, selfReportedByNode,
                    childOccupiedByParent, memo);
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

    @Override
    @Transactional(readOnly = true)
    public boolean isExecutionTreeClosed(Long executionId) {
        if (executionId == null) return false;
        List<ProductionTaskNode> nodes = taskNodeMapper.selectList(Wrappers.<ProductionTaskNode>lambdaQuery()
                .eq(ProductionTaskNode::getExecutionId, executionId));
        if (nodes.isEmpty()) return false;
        Map<Long, BigDecimal> childOcc = new HashMap<>();
        for (ProductionTaskNode n : nodes) {
            if (n.getParentNodeId() != null) {
                childOcc.merge(n.getParentNodeId(), effectiveOf(n), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> selfReportedByNode = loadSelfReportedByExecution(executionId);
        for (ProductionTaskNode n : nodes) {
            BigDecimal remain = floorZero(effectiveOf(n)
                    .subtract(childOcc.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO))
                    .subtract(selfReportedByNode.getOrDefault(n.getTaskNodeId(), BigDecimal.ZERO)));
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTreeEventVO> executionEvents(Long executionId) {
        if (executionId == null) return new ArrayList<>();
        List<TaskTreeEventVO> events = new ArrayList<>();
        // 1) 任务动作流水：sys_oper_log（分配/收回/退回），bizType=production_task，bizId=executionId
        try {
            jdbcTemplate.query(
                    "SELECT oper_url, oper_param, real_name, username, create_time FROM sys_oper_log "
                            + "WHERE biz_type = 'production_task' AND biz_id = ? AND status = 1 "
                            + "ORDER BY create_time ASC",
                    rs -> {
                        String url = rs.getString("oper_url");
                        String param = rs.getString("oper_param");
                        String operator = nvlStr(rs.getString("real_name"), rs.getString("username"));
                        java.sql.Timestamp ts = rs.getTimestamp("create_time");
                        LocalDateTime time = ts == null ? null : ts.toLocalDateTime();
                        if (url == null) return;
                        if (url.contains("/assign")) {
                            parseAssignEvents(events, param, operator, time);
                        } else if (url.contains("/recall")) {
                            parseNodeActionEvent(events, param, operator, time, url,
                                    "RECALL", "收回", true);
                        } else if (url.contains("/return")) {
                            parseNodeActionEvent(events, param, operator, time, url,
                                    "RETURN", "退回", false);
                        }
                    }, executionId);
        } catch (Exception e) {
            log.warn("查询任务操作流水失败 executionId={}: {}", executionId, e.getMessage());
        }
        // 2) 报工 / 撤销报工：production_work_report 动态事实
        try {
            List<ProductionWorkReport> reports = workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                    .eq(ProductionWorkReport::getExecutionId, executionId)
                    .orderByAsc(ProductionWorkReport::getReportTime));
            for (ProductionWorkReport r : reports) {
                TaskTreeEventVO vo = new TaskTreeEventVO();
                if (WorkReportStatusEnum.SUBMITTED.getCode().equals(r.getReportStatus())) {
                    vo.setAction("WORK_REPORT");
                    vo.setActionLabel("报工");
                    vo.setTime(r.getReportTime());
                    vo.setOperatorName(r.getReporterName());
                    vo.setTargetName(r.getReporterName());
                    vo.setQuantity(nvl(r.getQualifiedQuantity()).add(nvl(r.getDefectiveQuantity())));
                    vo.setRemark(r.getRemark());
                } else if (WorkReportStatusEnum.CANCELLED.getCode().equals(r.getReportStatus())) {
                    vo.setAction("WORK_REPORT_CANCEL");
                    vo.setActionLabel("撤销报工");
                    vo.setTime(r.getCancelledAt());
                    vo.setOperatorName(nvlStr(r.getCancelledByName(), r.getReporterName()));
                    vo.setTargetName(r.getReporterName());
                    vo.setQuantity(nvl(r.getQualifiedQuantity()).add(nvl(r.getDefectiveQuantity())));
                    vo.setRemark(r.getCancelReason());
                } else {
                    continue;
                }
                events.add(vo);
            }
        } catch (Exception e) {
            log.warn("查询报工流水失败 executionId={}: {}", executionId, e.getMessage());
        }
        events.sort(Comparator.comparing(TaskTreeEventVO::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    /** 解析分配流水（operParam = {"parentNodeId":..,"items":[{userId,quantity}]}）→ 每人一条事件 */
    private void parseAssignEvents(List<TaskTreeEventVO> events, String param, String operator, LocalDateTime time) {
        if (param == null || param.isBlank()) return;
        try {
            JSONObject root = JSONUtil.parseObj(param);
            JSONArray items = root.getJSONArray("items");
            if (items == null) return;
            for (Object o : items) {
                JSONObject item = (JSONObject) o;
                Long userId = item.getLong("userId");
                BigDecimal qty = item.getBigDecimal("quantity");
                if (userId == null || qty == null) continue;
                TaskTreeEventVO vo = new TaskTreeEventVO();
                vo.setAction("ASSIGN");
                vo.setActionLabel("分配任务");
                vo.setTime(time);
                vo.setOperatorName(operator);
                vo.setTargetName(assigneeNameOf(userId));
                vo.setQuantity(qty);
                events.add(vo);
            }
        } catch (Exception e) {
            log.warn("解析分配流水参数失败: {}", e.getMessage());
        }
    }

    /** 解析收回/退回流水（operParam = {"childNodeId|nodeId":..,"dto":{"quantity":..,"remark":..}}） */
    private void parseNodeActionEvent(List<TaskTreeEventVO> events, String param, String operator,
                                      LocalDateTime time, String url, String action, String label,
                                      boolean targetIsNodeHolder) {
        if (param == null || param.isBlank()) return;
        try {
            JSONObject root = JSONUtil.parseObj(param);
            JSONObject dto = root.getJSONObject("dto");
            if (dto == null) return;
            BigDecimal qty = dto.getBigDecimal("quantity");
            if (qty == null) return;
            Long nodeId = root.getLong(targetIsNodeHolder ? "childNodeId" : "nodeId");
            TaskTreeEventVO vo = new TaskTreeEventVO();
            vo.setAction(action);
            vo.setActionLabel(label);
            vo.setTime(time);
            vo.setOperatorName(operator);
            vo.setQuantity(qty);
            vo.setRemark(dto.getStr("remark"));
            ProductionTaskNode node = nodeId == null ? null : taskNodeMapper.selectById(nodeId);
            if (targetIsNodeHolder) {
                // 收回：涉及人员 = 子节点持有人（从李四收回10）
                vo.setTargetName(node == null ? null : node.getAssigneeName());
            } else {
                // 退回：涉及人员 = 父节点持有人（退回给上级）
                if (node != null && node.getParentNodeId() != null) {
                    ProductionTaskNode parent = taskNodeMapper.selectById(node.getParentNodeId());
                    vo.setTargetName(parent == null ? null : parent.getAssigneeName());
                }
            }
            events.add(vo);
        } catch (Exception e) {
            log.warn("解析收回/退回流水参数失败: {}", e.getMessage());
        }
    }

    private String nvlStr(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
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

    /** 多工序版本：一次加载多道工序全部有效报工，按 taskNodeId 聚合 selfReported */
    private Map<Long, BigDecimal> loadSelfReportedByExecutions(java.util.Collection<Long> executionIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (executionIds == null || executionIds.isEmpty()) return map;
        List<ProductionWorkReport> reports = workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                .in(ProductionWorkReport::getExecutionId, executionIds)
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
