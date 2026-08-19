package com.jjx.production.migration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.system.domain.entity.SysDept;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy production_dispatch.operators → production_dispatch_node 迁移执行器
 * <p>
 * P1-A：仅编写与单测，不执行正式 backfill（正式执行属 P1-E）。
 * <p>
 * 规则：
 * - 空 operators → 不创建 Node（dispatch 保持待派状态）
 * - 1 个 operator → 1 个 ACTIVE Node
 * - 多个 operator → 按 legacy JSON 数组稳定顺序生成链：前 N-1 个 DELEGATED、最后 1 个 ACTIVE；parentNodeId 顺序串联
 * - assignedAt 优先 dispatch.assignTime；assignedBy 优先 dispatch.assignedBy（无法恢复逐级真实值时不编造，沿用容器值）
 * - org 快照：按当前 user/dept 重建（非真实历史），remark 标记 ORG_RECONSTRUCTED
 * - 幂等：同一 dispatch 已有节点则跳过；可重复运行
 * - 异常：非法 JSON 等 → 记录 dispatchId + 跳过，不中断整体
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchNodeBackfill {

    private final ProductionDispatchNodeMapper nodeMapper;
    private final ProductionDispatchMapper dispatchMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;

    /** 迁移统计结果 */
    @lombok.Data
    public static class BackfillResult {
        private int scanned;
        private int migrated;
        private int skipped;
        private int errors;
        private final List<String> errorMessages = new ArrayList<>();
    }

    /**
     * Dry-run（P1-E 迁移前预检）：只解析不写库，输出每条 dispatch 的预期链。
     * migration-only 能力，不触碰业务 Service。
     */
    public BackfillResult dryRunAll() {
        BackfillResult result = new BackfillResult();
        List<ProductionDispatch> dispatches = dispatchMapper.selectList(
                Wrappers.<ProductionDispatch>lambdaQuery()
                        .isNotNull(ProductionDispatch::getOperators)
                        .ne(ProductionDispatch::getOperators, "")
                        .orderByAsc(ProductionDispatch::getDispatchId));
        for (ProductionDispatch d : dispatches) {
            result.setScanned(result.getScanned() + 1);
            try {
                List<DispatchNodeBackfillParser.NodeDraft> drafts =
                        DispatchNodeBackfillParser.parseChain(d.getOperators());
                boolean hasNode = hasNodes(d.getDispatchId());
                boolean ambiguous = drafts.stream().anyMatch(x -> x.ambiguous);
                StringBuilder sb = new StringBuilder();
                sb.append("dispatchId=").append(d.getDispatchId())
                        .append(" operators=").append(d.getOperators())
                        .append(" → 预计节点=").append(drafts.size())
                        .append(" (");
                for (int i = 0; i < drafts.size(); i++) {
                    if (i > 0) sb.append(" → ");
                    sb.append(drafts.get(i).assigneeName);
                    if (i == drafts.size() - 1) sb.append(" ACTIVE");
                }
                sb.append(")");
                if (hasNode) {
                    sb.append(" [SKIP: 已有Node]");
                    result.setSkipped(result.getSkipped() + 1);
                } else if (drafts.isEmpty()) {
                    sb.append(" [SKIP: 无执行人]");
                    result.setSkipped(result.getSkipped() + 1);
                } else {
                    if (ambiguous) sb.append(" [LEGACY_AMBIGUOUS_ORDER]");
                    result.setMigrated(result.getMigrated() + 1);
                }
                result.getErrorMessages().add(sb.toString()); // dry-run 明细复用 errorMessages 通道输出
                log.info("[DRY-RUN] {}", sb);
            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                result.getErrorMessages().add("dispatchId=" + d.getDispatchId() + ": " + e.getMessage());
                log.warn("[DRY-RUN] 解析异常 dispatchId={}: {}", d.getDispatchId(), e.getMessage());
            }
        }
        return result;
    }

    private boolean hasNodes(Long dispatchId) {
        Long cnt = nodeMapper.selectCount(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatchId));
        return cnt != null && cnt > 0;
    }

    /**
     * 全量 backfill：所有存在 operators 的 dispatch。幂等，可重复运行。
     */
    @Transactional(rollbackFor = Exception.class)
    public BackfillResult backfillAll() {
        BackfillResult result = new BackfillResult();
        List<ProductionDispatch> dispatches = dispatchMapper.selectList(
                Wrappers.<ProductionDispatch>lambdaQuery()
                        .isNotNull(ProductionDispatch::getOperators)
                        .ne(ProductionDispatch::getOperators, "")
                        .orderByAsc(ProductionDispatch::getDispatchId));
        for (ProductionDispatch d : dispatches) {
            result.setScanned(result.getScanned() + 1);
            try {
                int created = backfillDispatch(d);
                if (created < 0) {
                    result.setSkipped(result.getSkipped() + 1);
                } else if (created == 0) {
                    result.setSkipped(result.getSkipped() + 1);
                } else {
                    result.setMigrated(result.getMigrated() + 1);
                }
            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                result.getErrorMessages().add("dispatchId=" + d.getDispatchId() + ": " + e.getMessage());
                log.warn("backfill 跳过异常 dispatchId={}: {}", d.getDispatchId(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 单 dispatch backfill。返回创建节点数；-1=已有节点跳过；0=无执行人不创建。
     */
    @Transactional(rollbackFor = Exception.class)
    public int backfillDispatch(Long dispatchId) {
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) throw new IllegalArgumentException("dispatch 不存在: " + dispatchId);
        return backfillDispatch(d);
    }

    private int backfillDispatch(ProductionDispatch d) {
        // 幂等：已存在节点 → 跳过
        Long exist = nodeMapper.selectCount(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, d.getDispatchId()));
        if (exist != null && exist > 0) {
            log.info("backfill 跳过（已有节点）dispatchId={}", d.getDispatchId());
            return -1;
        }
        List<DispatchNodeBackfillParser.NodeDraft> drafts;
        try {
            drafts = DispatchNodeBackfillParser.parseChain(d.getOperators());
        } catch (DispatchNodeBackfillParser.BackfillParseException e) {
            log.warn("backfill 解析异常 dispatchId={}: {}", d.getDispatchId(), e.getMessage());
            throw e; // 由 backfillAll 捕获计入 errors 并跳过
        }
        if (drafts.isEmpty()) {
            log.info("backfill 无执行人（不创建节点）dispatchId={}", d.getDispatchId());
            return 0;
        }
        boolean anyAmbiguous = drafts.stream().anyMatch(x -> x.ambiguous);
        String baseRemark = DispatchNodeBackfillParser.MARKER_LEGACY_BACKFILL
                + (anyAmbiguous ? "," + DispatchNodeBackfillParser.MARKER_AMBIGUOUS_ORDER : "");

        Long prevNodeId = null;
        int size = drafts.size();
        int created = 0;
        for (int i = 0; i < size; i++) {
            DispatchNodeBackfillParser.NodeDraft draft = drafts.get(i);
            boolean last = (i == size - 1);
            ProductionDispatchNode node = new ProductionDispatchNode();
            node.setDispatchId(d.getDispatchId());
            node.setParentNodeId(prevNodeId);
            node.setAssigneeType("USER");
            node.setAssigneeId(draft.assigneeId);
            node.setAssigneeName(draft.assigneeName);
            fillOrgSnapshot(node, draft.assigneeId);
            node.setNodeStatus(last ? DispatchNodeStatusEnum.ACTIVE.getCode()
                    : DispatchNodeStatusEnum.DELEGATED.getCode());
            node.setAssignedBy(d.getAssignedBy());
            node.setAssignedByName(d.getAssignedByName());
            node.setAssignedAt(d.getAssignTime() != null ? d.getAssignTime() : d.getCreateTime());
            node.setRemark(baseRemark);
            node.setCreateBy("LEGACY_BACKFILL");
            nodeMapper.insert(node);
            prevNodeId = node.getNodeId();
            created++;
        }
        log.info("backfill 完成 dispatchId={}, 创建节点={}", d.getDispatchId(), created);
        return created;
    }

    /**
     * 组织快照：按当前 user/dept 重建（非真实历史），org 可用则填并标记 ORG_RECONSTRUCTED。
     */
    private void fillOrgSnapshot(ProductionDispatchNode node, Long userId) {
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null || u.getDeptId() == null) return;
            node.setOrgId(u.getDeptId());
            node.setOrgName(deptNameOf(u.getDeptId()));
            node.setOrgPath(deptPathOf(u.getDeptId()));
            // 组织为迁移时重建，追加标记
            String remark = node.getRemark();
            node.setRemark(remark == null ? DispatchNodeBackfillParser.MARKER_ORG_RECONSTRUCTED
                    : remark + "," + DispatchNodeBackfillParser.MARKER_ORG_RECONSTRUCTED);
        } catch (Exception e) {
            log.warn("backfill 组织快照重建失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private String deptNameOf(Long deptId) {
        SysDept d = sysDeptMapper.selectById(deptId);
        return d == null ? null : d.getDeptName();
    }

    /** 祖先路径快照（如 "1/5/6/7"）：sys_dept 无 ancestors 列，由 parent_id 递归计算 */
    private String deptPathOf(Long deptId) {
        List<String> path = new ArrayList<>();
        Long cur = deptId;
        int guard = 0;
        while (cur != null && guard++ < 20) {
            SysDept d = sysDeptMapper.selectById(cur);
            if (d == null) break;
            path.add(0, String.valueOf(d.getId()));
            if (d.getParentId() == null || d.getParentId() == 0L) break;
            cur = d.getParentId();
        }
        return path.isEmpty() ? null : String.join("/", path);
    }

    /**
     * 回滚（P1-A 设计，P1-E 正式执行前/后如需重做均可用）：
     * 仅删除本迁移标记（LEGACY_BACKFILL）创建的节点；未来 P1 业务生成的 Node 不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public int rollbackBackfilled() {
        return nodeMapper.delete(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .like(ProductionDispatchNode::getRemark, DispatchNodeBackfillParser.MARKER_LEGACY_BACKFILL));
    }

    /** 指定 dispatch 的 backfill 节点回滚 */
    @Transactional(rollbackFor = Exception.class)
    public int rollbackDispatch(Long dispatchId) {
        return nodeMapper.delete(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatchId)
                .like(ProductionDispatchNode::getRemark, DispatchNodeBackfillParser.MARKER_LEGACY_BACKFILL));
    }
}
