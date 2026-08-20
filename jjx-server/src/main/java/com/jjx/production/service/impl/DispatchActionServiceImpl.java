package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.vo.DispatchVO;
import com.jjx.production.enums.DispatchLogActionEnum;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.enums.DispatchStatusEnum;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.migration.DispatchNodeBackfillParser;
import com.jjx.production.service.DispatchActionService;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 派工责任链动作服务实现（P1-C：Node 化写入）
 * <p>
 * 统一事务模板（每动作）：
 * 1. 锁 dispatch 行（SELECT ... FOR UPDATE）→ 同一 dispatch 责任流转串行
 * 2. legacy on-write adoption（无 Node 且 operators 有数据时，事务内转 Node）
 * 3. 加载当前 ACTIVE Node + 校验前置条件
 * 4. 校验操作人权限
 * 5. 条件关闭当前 ACTIVE（WHERE node_status='ACTIVE'，affectedRows 必须 =1）
 * 6. 创建新 ACTIVE Node
 * 7. 写 DispatchLog
 * 8. 从 Node 重建 operators projection（当前有效责任路径，非完整历史）
 * 9. 更新 dispatch 必要兼容字段
 * 10. commit；任一步失败全部回滚
 * <p>
 * 并发保护：dispatch 行锁（串行）+ 条件更新（affectedRows）+ UNIQUE(dispatch_id, active_guard) 兜底。
 * 不用分布式锁（单体应用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchActionServiceImpl implements DispatchActionService {

    private final ProductionDispatchMapper dispatchMapper;
    private final ProductionDispatchNodeMapper nodeMapper;
    private final ProductionDispatchLogMapper dispatchLogMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final DispatchNodeReadService nodeReadService;
    private final JdbcTemplate jdbcTemplate;

    /** on-write adoption 标记（与 P1-E LEGACY_BACKFILL 区分，避免回滚混淆） */
    public static final String MARKER_ON_WRITE_ADOPTION = "LEGACY_ON_WRITE_ADOPTION";

    // ==================== ASSIGN ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchVO assign(Long executionId, Long orderId, Long targetUserId, Long equipmentId,
                             String remark, String operatorName, Long operatorId) {
        // 初始派工权限：超管 或 production:dispatch:assign（P0-04 定稿，不用 isDispatched/deptId）
        checkInitialAssignRight(operatorId);
        if (executionId == null) throw new BusinessException("缺少工序执行ID");
        if (orderId == null) throw new BusinessException("缺少工单ID");
        if (targetUserId == null) throw new BusinessException("缺少责任人");
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null) throw new BusinessException("责任人不存在");

        // 前置：execution 有效
        ProductionOperationExecution exec = executionMapper.selectById(executionId);
        if (exec == null) throw new BusinessException("工序执行记录不存在");
        if (exec.getExecutionStatus() != null
                && (exec.getExecutionStatus() == 4 || exec.getExecutionStatus() == 6)) {
            throw new BusinessException("工序已完成/取消，不可派工");
        }

        // 找/建 dispatch 容器（execution_id 1:1）
        ProductionDispatch d = dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
                .eq(ProductionDispatch::getExecutionId, executionId).last("LIMIT 1"));
        if (d == null) {
            d = new ProductionDispatch();
            d.setOrderId(orderId);
            d.setExecutionId(executionId);
            d.setProcessName(exec.getProcessName());
            d.setProcessOrder(exec.getProcessOrder());
            d.setOrderNo(orderNoOf(orderId));
            d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
            d.setReDispatchCount(0);
            d.setCreateBy(operatorName);
            dispatchMapper.insert(d);
        }
        // 锁 dispatch 行（后续所有动作统一在此锁）
        lockDispatch(d.getDispatchId());

        // 已有 ACTIVE Node → 拒绝重复 ASSIGN
        if (nodeReadService.getCurrentActiveNode(d.getDispatchId()) != null) {
            throw new BusinessException("该工序已派工，应使用继续派工/改派");
        }
        // 无 Node 且 operators 有数据（legacy-only，如整单退回后重新指派）→ on-write adoption 先接管历史链
        adoptLegacyIfNeeded(d.getDispatchId());

        // 创建 root ACTIVE Node
        ProductionDispatchNode node = createActiveNode(d.getDispatchId(), null, targetUserId,
                operatorId, operatorName);
        nodeMapper.insert(node);

        // dispatch 兼容字段
        d.setAssignedBy(operatorId);
        d.setAssignedByName(operatorName);
        d.setAssignTime(LocalDateTime.now());
        d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
        d.setRejectReason(null);
        d.setUpdateBy(operatorName);
        if (equipmentId != null) {
            d.setEquipmentId(equipmentId);
            d.setEquipmentName(equipmentNameOf(equipmentId));
        }
        dispatchMapper.updateById(d);

        // projection + log
        syncOperatorsProjection(d.getDispatchId());
        addLog(d.getDispatchId(), d.getOrderId(), DispatchLogActionEnum.ASSIGN,
                "指派：" + target.getNickName() + "（第 1 级责任人），主管：" + operatorName,
                operatorName, operatorId);
        return buildVO(d.getDispatchId());
    }

    // ==================== DELEGATE ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchVO delegate(Long dispatchId, Long targetUserId, String remark,
                               String operatorName, Long operatorId) {
        ProductionDispatch d = lockAndGet(dispatchId);
        checkExecutionNotFrozen(d);
        adoptLegacyIfNeeded(dispatchId);

        ProductionDispatchNode active = requireActive(dispatchId);
        // 权限：当前 ACTIVE assignee 本人 / 超管 / 有 delegate 权限者（代操作）
        checkNodeOperatorRight(active, operatorId);

        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null) throw new BusinessException("责任人不存在");
        if (targetUserId.equals(active.getAssigneeId())) {
            throw new BusinessException("不能派给当前责任人本人");
        }
        // 目标范围：沿用现有组织规则（目标须在当前责任人可派范围内=其手下/自己手下；保留兼容）
        checkDelegateTargetInScope(active, targetUserId);

        // 条件关闭当前 ACTIVE
        int closed = closeActiveNode(active.getNodeId(), DispatchNodeStatusEnum.DELEGATED, remark);
        if (closed != 1) throw new BusinessException("任务已被其他人处理，请刷新后重试");

        // 创建新 ACTIVE（parent=原 ACTIVE）
        ProductionDispatchNode newNode = createActiveNode(dispatchId, active.getNodeId(),
                targetUserId, operatorId, operatorName);
        nodeMapper.insert(newNode);

        d.setAssignedBy(operatorId);
        d.setAssignedByName(operatorName);
        d.setAssignTime(LocalDateTime.now());
        d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);

        syncOperatorsProjection(dispatchId);
        addLog(dispatchId, d.getOrderId(), DispatchLogActionEnum.DELEGATE,
                operatorName + " 将责任从 " + active.getAssigneeName() + " 下派给 " + target.getNickName(),
                operatorName, operatorId);
        return buildVO(dispatchId);
    }

    // ==================== REASSIGN ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchVO reassign(Long dispatchId, Long targetUserId, String reason,
                               String operatorName, Long operatorId) {
        ProductionDispatch d = lockAndGet(dispatchId);
        checkExecutionNotFrozen(d);
        adoptLegacyIfNeeded(dispatchId);

        ProductionDispatchNode active = requireActive(dispatchId);
        // 权限：超管 / 有 reassign 权限者（当前责任人本人禁止自改派）
        checkReassignRight(active, operatorId);

        SysUser target = sysUserMapper.selectById(targetUserId);
        if (target == null) throw new BusinessException("责任人不存在");
        if (targetUserId.equals(active.getAssigneeId())) {
            throw new BusinessException("不能改派给当前责任人本人");
        }

        // 条件关闭当前 ACTIVE → REASSIGNED（历史不可覆盖）
        int closed = closeActiveNode(active.getNodeId(), DispatchNodeStatusEnum.REASSIGNED, reason);
        if (closed != 1) throw new BusinessException("任务已被其他人处理，请刷新后重试");

        // 创建新 ACTIVE（同层：parent=原 ACTIVE.parentNodeId）
        ProductionDispatchNode newNode = createActiveNode(dispatchId, active.getParentNodeId(),
                targetUserId, operatorId, operatorName);
        nodeMapper.insert(newNode);

        d.setAssignedBy(operatorId);
        d.setAssignedByName(operatorName);
        d.setAssignTime(LocalDateTime.now());
        d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
        d.setReDispatchCount((d.getReDispatchCount() == null ? 0 : d.getReDispatchCount()) + 1);
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);

        syncOperatorsProjection(dispatchId);
        addLog(dispatchId, d.getOrderId(), DispatchLogActionEnum.REASSIGN,
                operatorName + " 将第 " + active.getAssigneeName() + " 改派为 " + target.getNickName()
                        + (reason != null ? "（" + reason + "）" : ""),
                operatorName, operatorId);
        return buildVO(dispatchId);
    }

    // ==================== RETURN ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchVO returnTask(Long dispatchId, String reason,
                                 String operatorName, Long operatorId) {
        ProductionDispatch d = lockAndGet(dispatchId);
        checkExecutionNotFrozen(d);
        adoptLegacyIfNeeded(dispatchId);

        ProductionDispatchNode active = requireActive(dispatchId);
        // 权限：当前 ACTIVE assignee 本人 / 超管 / 有 return 权限者（代操作）
        checkReturnRight(active, operatorId);

        // 已是第一责任层（root）→ 拒绝（整单退回走旧 reject 兼容）
        if (active.getParentNodeId() == null) {
            throw new BusinessException("当前任务已是最上级责任节点，无法继续退回");
        }
        // 读取原上一级责任快照（即使 parent 已是 DELEGATED，只读快照，不激活）
        ProductionDispatchNode parent = nodeMapper.selectById(active.getParentNodeId());
        if (parent == null) {
            throw new BusinessException("上级责任节点不存在，数据异常");
        }

        // 条件关闭当前 ACTIVE → RETURNED
        int closed = closeActiveNode(active.getNodeId(), DispatchNodeStatusEnum.RETURNED, reason);
        if (closed != 1) throw new BusinessException("任务已被其他人处理，请刷新后重试");

        // 创建新的上级责任实例 N4：
        // assignee/org 快照 = parent 原始快照；parentNodeId = parent.parentNodeId（同层，非被退回节点）
        ProductionDispatchNode newNode = new ProductionDispatchNode();
        newNode.setDispatchId(dispatchId);
        newNode.setParentNodeId(parent.getParentNodeId());
        newNode.setAssigneeType(parent.getAssigneeType());
        newNode.setAssigneeId(parent.getAssigneeId());
        newNode.setAssigneeName(parent.getAssigneeName());
        newNode.setOrgId(parent.getOrgId());
        newNode.setOrgName(parent.getOrgName());
        newNode.setOrgPath(parent.getOrgPath());
        newNode.setNodeStatus(DispatchNodeStatusEnum.ACTIVE.getCode());
        newNode.setAssignedBy(operatorId);
        newNode.setAssignedByName(operatorName);
        newNode.setAssignedAt(LocalDateTime.now());
        newNode.setRemark(reason);
        newNode.setCreateBy(operatorName);
        nodeMapper.insert(newNode);

        d.setAssignedBy(operatorId);
        d.setAssignedByName(operatorName);
        d.setAssignTime(LocalDateTime.now());
        d.setStatus(DispatchStatusEnum.ASSIGNED.getCode());
        d.setUpdateBy(operatorName);
        dispatchMapper.updateById(d);

        syncOperatorsProjection(dispatchId);
        // DispatchLog：content 完整表达 fromNodeId/toNewNodeId/原责任人/返回到的上级
        addLog(dispatchId, d.getOrderId(), DispatchLogActionEnum.RETURN,
                operatorName + " 退回：" + active.getAssigneeName()
                        + "（fromNodeId=" + active.getNodeId() + "）→ 上级责任层 "
                        + parent.getAssigneeName() + "（toNewNodeId=" + newNode.getNodeId()
                        + "，parentNodeId=" + (parent.getParentNodeId() == null ? "null" : parent.getParentNodeId())
                        + "）" + (reason != null ? "，原因：" + reason : ""),
                operatorName, operatorId);
        return buildVO(dispatchId);
    }

    // ==================== 统一 helpers ====================

    /** 锁 dispatch 行（SELECT ... FOR UPDATE），确保同一 dispatch 责任流转串行 */
    private ProductionDispatch lockAndGet(Long dispatchId) {
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new BusinessException("派工单不存在");
        lockDispatch(dispatchId);
        return d;
    }

    /** WP-C：责任链冻结——Execution 已完成/已取消时禁止任何责任动作 */
    private void checkExecutionNotFrozen(ProductionDispatch d) {
        try {
            ProductionOperationExecution exec = executionMapper.selectById(d.getExecutionId());
            if (exec == null) return;
            if (ExecutionStatusEnum.COMPLETED.getCode().equals(exec.getExecutionStatus())
                    || ExecutionStatusEnum.CANCELLED.getCode().equals(exec.getExecutionStatus())) {
                throw new BusinessException("工序已完成/取消，责任链冻结，仅可查看");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("责任链冻结校验异常(跳过): {}", e.getMessage());
        }
    }

    private void lockDispatch(Long dispatchId) {
        // SELECT ... FOR UPDATE：锁 dispatch 容器行，同一 dispatch 责任流转串行（事务结束释放）
        dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
                .eq(ProductionDispatch::getDispatchId, dispatchId)
                .last("FOR UPDATE"));
    }

    /** 当前 ACTIVE 节点（无则报错） */
    private ProductionDispatchNode requireActive(Long dispatchId) {
        ProductionDispatchNode active = nodeMapper.selectOne(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatchId)
                .eq(ProductionDispatchNode::getNodeStatus, DispatchNodeStatusEnum.ACTIVE.getCode())
                .last("LIMIT 1"));
        if (active == null) throw new BusinessException("当前没有进行中的派工责任节点");
        return active;
    }

    /** 条件关闭 ACTIVE（affectedRows 必须 =1） */
    private int closeActiveNode(Long nodeId, DispatchNodeStatusEnum toStatus, String remark) {
        ProductionDispatchNode upd = new ProductionDispatchNode();
        upd.setNodeId(nodeId);
        upd.setNodeStatus(toStatus.getCode());
        upd.setClosedAt(LocalDateTime.now());
        upd.setRemark(remark);
        return nodeMapper.update(upd, Wrappers.<ProductionDispatchNode>lambdaUpdate()
                .eq(ProductionDispatchNode::getNodeId, nodeId)
                .eq(ProductionDispatchNode::getNodeStatus, DispatchNodeStatusEnum.ACTIVE.getCode()));
    }

    /** 统一 Node 创建（assignee/org 快照 + assignedBy/assignedAt） */
    private ProductionDispatchNode createActiveNode(Long dispatchId, Long parentNodeId,
                                                    Long targetUserId, Long operatorId, String operatorName) {
        SysUser u = sysUserMapper.selectById(targetUserId);
        if (u == null) throw new BusinessException("责任人不存在");
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setDispatchId(dispatchId);
        n.setParentNodeId(parentNodeId);
        n.setAssigneeType("USER");
        n.setAssigneeId(u.getUserId());
        n.setAssigneeName(displayName(u));
        fillOrgSnapshot(n, u);
        n.setNodeStatus(DispatchNodeStatusEnum.ACTIVE.getCode());
        n.setAssignedBy(operatorId);
        n.setAssignedByName(operatorName);
        n.setAssignedAt(LocalDateTime.now());
        n.setCreateBy(operatorName);
        return n;
    }

    /** 组织快照：目标用户当前所属部门（无部门允许为 null，不阻止派给有效 USER） */
    private void fillOrgSnapshot(ProductionDispatchNode n, SysUser u) {
        if (u.getDeptId() == null) return;
        SysDept dept = sysDeptMapper.selectById(u.getDeptId());
        if (dept == null) return;
        n.setOrgId(dept.getId());
        n.setOrgName(dept.getDeptName());
        n.setOrgPath(deptPathOf(dept));
    }

    private String deptPathOf(SysDept dept) {
        List<String> path = new ArrayList<>();
        SysDept cur = dept;
        int guard = 0;
        while (cur != null && guard++ < 20) {
            path.add(0, String.valueOf(cur.getId()));
            if (cur.getParentId() == null || cur.getParentId() == 0L) break;
            cur = sysDeptMapper.selectById(cur.getParentId());
        }
        return path.isEmpty() ? null : String.join("/", path);
    }

    private String displayName(SysUser u) {
        return u.getNickName() != null && !u.getNickName().isBlank() ? u.getNickName() : u.getUserName();
    }

    /**
     * legacy on-write adoption：无 Node 且 operators 有数据时，同一事务内把 legacy 链转 Node，
     * 然后继续本次动作。remark = LEGACY_ON_WRITE_ADOPTION（与 P1-E LEGACY_BACKFILL 区分）。
     * <p>
     * Compatibility safety net after cutover. Not normal business flow.
     * P1-E 正式 backfill 后旧 3 条数据已全部迁移，此路径仅用于未来发现未迁移 legacy dispatch 的异常兼容。
     */
    private void adoptLegacyIfNeeded(Long dispatchId) {
        if (nodeReadService.hasNodes(dispatchId)) return;
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null || d.getOperators() == null || d.getOperators().isBlank()) return;

        List<DispatchNodeBackfillParser.NodeDraft> drafts;
        try {
            drafts = DispatchNodeBackfillParser.parseChain(d.getOperators());
        } catch (DispatchNodeBackfillParser.BackfillParseException e) {
            throw new BusinessException("遗留派工数据无法解析，请先修复 operators 数据（" + e.getMessage() + "）");
        }
        if (drafts.isEmpty()) return;

        Long prevNodeId = null;
        for (int i = 0; i < drafts.size(); i++) {
            DispatchNodeBackfillParser.NodeDraft draft = drafts.get(i);
            boolean last = (i == drafts.size() - 1);
            ProductionDispatchNode n = new ProductionDispatchNode();
            n.setDispatchId(dispatchId);
            n.setParentNodeId(prevNodeId);
            n.setAssigneeType("USER");
            n.setAssigneeId(draft.assigneeId);
            n.setAssigneeName(draft.assigneeName);
            if (draft.assigneeId != null) {
                SysUser u = sysUserMapper.selectById(draft.assigneeId);
                if (u != null) fillOrgSnapshot(n, u);
            }
            n.setNodeStatus(last ? DispatchNodeStatusEnum.ACTIVE.getCode()
                    : DispatchNodeStatusEnum.DELEGATED.getCode());
            n.setAssignedBy(d.getAssignedBy());
            n.setAssignedByName(d.getAssignedByName());
            n.setAssignedAt(d.getAssignTime() != null ? d.getAssignTime() : d.getCreateTime());
            n.setRemark(MARKER_ON_WRITE_ADOPTION);
            n.setCreateBy("ON_WRITE_ADOPTION");
            nodeMapper.insert(n);
            prevNodeId = n.getNodeId();
        }
        log.info("on-write adoption 完成 dispatchId={}, 生成节点={}", dispatchId, drafts.size());
    }

    /** 从 Node 重建 legacy operators projection（当前有效责任路径，非完整历史） */
    private void syncOperatorsProjection(Long dispatchId) {
        List<ProductionDispatchNode> nodes = nodeMapper.selectList(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatchId));
        if (nodes.isEmpty()) return;

        // 找到当前 ACTIVE；从 ACTIVE 沿 parentNodeId 向上追溯，反转得到当前有效路径
        ProductionDispatchNode active = nodes.stream()
                .filter(n -> DispatchNodeStatusEnum.ACTIVE.getCode().equals(n.getNodeStatus()))
                .findFirst().orElse(null);
        List<ProductionDispatchNode> path = new ArrayList<>();
        if (active != null) {
            ProductionDispatchNode cur = active;
            int guard = 0;
            while (cur != null && guard++ < 50) {
                path.add(0, cur);
                if (cur.getParentNodeId() == null) break;
                Long pid = cur.getParentNodeId();
                cur = nodes.stream().filter(n -> n.getNodeId().equals(pid)).findFirst().orElse(null);
            }
        }
        // 无 ACTIVE（如整单退回后）→ 保留当前 projection 不生成空数组（旧页面展示最后链）
        if (path.isEmpty()) return;

        // 生成 [{userId,userName,level}]，level 按路径顺序（Legacy Projection only，非业务层级）
        StringBuilder sb = new StringBuilder("[");
        int lv = 1;
        for (ProductionDispatchNode n : path) {
            if (sb.length() > 1) sb.append(",");
            sb.append("{\"userId\":").append(n.getAssigneeId())
                    .append(",\"userName\":\"").append(n.getAssigneeName() == null ? "" : n.getAssigneeName())
                    .append("\",\"level\":").append(lv++).append("}");
        }
        sb.append("]");

        ProductionDispatch upd = new ProductionDispatch();
        upd.setDispatchId(dispatchId);
        upd.setOperators(sb.toString());
        dispatchMapper.updateById(upd);
    }

    // ==================== 权限 ====================

    private void checkInitialAssignRight(Long operatorId) {
        if (SecurityUtils.hasPermission("*:*:*")) return;
        if (SecurityUtils.hasPermission("production:dispatch:assign")) return;
        throw new BusinessException("无初始派工权限");
    }

    private void checkNodeOperatorRight(ProductionDispatchNode active, Long operatorId) {
        if (SecurityUtils.hasPermission("*:*:*")) return;
        // WP-C：下派 = 当前 ACTIVE 责任人本人；有 delegate 权限者可代操作（不再因 assign 权限放行普通用户）
        if (SecurityUtils.hasPermission("production:dispatch:delegate")) return;
        if (operatorId != null && operatorId.equals(active.getAssigneeId())) return; // ACTIVE 本人
        throw new BusinessException("只有当前责任人本人或拥有下派权限的管理员可以下派");
    }

    private void checkReassignRight(ProductionDispatchNode active, Long operatorId) {
        if (SecurityUtils.hasPermission("*:*:*")) return;
        // WP-C：改派 = 上层调度者/超管；当前责任人本人禁止自改派（防绕过 RETURN）
        if (SecurityUtils.hasPermission("production:dispatch:reassign")) return;
        if (operatorId != null && operatorId.equals(active.getAssigneeId())) {
            throw new BusinessException("当前责任人本人不能改派自己，如需交接请使用下派或退回");
        }
        throw new BusinessException("无改派权限（需生产调度/改派权限）");
    }

    private void checkReturnRight(ProductionDispatchNode active, Long operatorId) {
        if (SecurityUtils.hasPermission("*:*:*")) return;
        // WP-C：退回 = 当前 ACTIVE 责任人本人；有 return 权限者可代操作
        if (SecurityUtils.hasPermission("production:dispatch:return")) return;
        if (operatorId != null && operatorId.equals(active.getAssigneeId())) return; // ACTIVE 本人可退回
        throw new BusinessException("只有当前责任人本人或拥有退回权限的管理员可以退回");
    }

    /** DELEGATE 目标范围：沿用现有组织规则（目标须在当前责任人手下；保留兼容，不重构候选算法） */
    private void checkDelegateTargetInScope(ProductionDispatchNode active, Long targetUserId) {
        // 目标须为有效用户（已在前面校验）；组织候选范围由前端 underlings 限制。
        // 与旧 appendLevel 一致的"手下"校验：目标属于当前责任人的手下（负责部门+下级部门成员）。
        List<Long> underlings = underlingUserIds(active.getAssigneeId());
        if (underlings.isEmpty()) {
            // 兜底：查询失败不阻塞（不因候选算法问题阻塞派工；前端已限制）
            return;
        }
        if (!underlings.contains(targetUserId)) {
            throw new BusinessException("目标用户不在当前责任人可派范围内（其负责部门及下级部门）");
        }
    }

    /** 某人手下的 userId 集合（复用现有递归部门树逻辑，与 DispatchServiceImpl.underlings 一致） */
    private List<Long> underlingUserIds(Long userId) {
        List<Long> ids = new ArrayList<>();
        if (userId == null) return ids;
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null || u.getUserName() == null) return ids;
            jdbcTemplate.query(
                    "WITH RECURSIVE dept_tree AS ("
                            + "  SELECT dept_id FROM sys_dept WHERE leader = ? AND del_flag = '0'"
                            + "  UNION ALL"
                            + "  SELECT d.dept_id FROM sys_dept d JOIN dept_tree t ON d.parent_id = t.dept_id WHERE d.del_flag = '0'"
                            + ") SELECT u.user_id FROM sys_user u"
                            + " WHERE u.dept_id IN (SELECT dept_id FROM dept_tree)"
                            + " AND u.status = 0 AND u.del_flag = '0' AND u.user_id != ?",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            ids.add(rs.getLong("user_id")), u.getUserName(), userId);
        } catch (Exception e) {
            log.warn("查询手下失败 userId={}: {}", userId, e.getMessage());
        }
        return ids;
    }

    // ==================== 辅助 ====================

    private void addLog(Long dispatchId, Long orderId, DispatchLogActionEnum action, String content,
                        String operatorName, Long operatorId) {
        ProductionDispatchLog log_ = new ProductionDispatchLog();
        log_.setDispatchId(dispatchId);
        log_.setOrderId(orderId);
        log_.setAction(action.getCode());
        log_.setContent(content);
        log_.setOperatorId(operatorId);
        log_.setOperatorName(operatorName);
        log_.setCreateTime(LocalDateTime.now());
        dispatchLogMapper.insert(log_);
    }

    private DispatchVO buildVO(Long dispatchId) {
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new BusinessException("派工单不存在");
        DispatchVO vo = DispatchVO.fromEntity(d);
        com.jjx.production.domain.vo.DispatchNodeVO cur = nodeReadService.getCurrentActiveNode(dispatchId);
        if (cur != null) {
            vo.setCurrentNodeId(cur.getNodeId());
            vo.setCurrentAssigneeId(cur.getAssigneeId());
            vo.setCurrentAssigneeName(cur.getAssigneeName());
            vo.setCurrentOrgId(cur.getOrgId());
            vo.setCurrentOrgName(cur.getOrgName());
            vo.setAssigneeSource(cur.getSource());
        }
        return vo;
    }

    private String orderNoOf(Long orderId) {
        try {
            List<String> nos = jdbcTemplate.query(
                    "SELECT order_no FROM production_order WHERE order_id = ?",
                    (rs, i) -> rs.getString("order_no"), orderId);
            return nos.isEmpty() ? null : nos.get(0);
        } catch (Exception e) {
            log.warn("查询工单编号失败 orderId={}: {}", orderId, e.getMessage());
            return null;
        }
    }

    private String equipmentNameOf(Long equipmentId) {
        if (equipmentId == null) return null;
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT equipment_name FROM production_equipment WHERE equipment_id = ?",
                    (rs, i) -> rs.getString("equipment_name"), equipmentId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            log.warn("查询设备失败 equipmentId={}: {}", equipmentId, e.getMessage());
            return null;
        }
    }
}
