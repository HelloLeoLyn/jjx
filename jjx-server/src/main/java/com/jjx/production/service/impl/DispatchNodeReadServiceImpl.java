package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.vo.DispatchNodeComparisonVO;
import com.jjx.production.domain.vo.DispatchNodeVO;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.service.DispatchNodeReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 派工责任链节点读取服务实现（P1-B Node-first Read Model）
 * <p>
 * Legacy fallback until P1-E cutover.
 * production_dispatch_node = new source of truth;
 * production_dispatch.operators = legacy projection, read fallback only.
 * Do not form permanent dual-source business model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchNodeReadServiceImpl implements DispatchNodeReadService {

    private final ProductionDispatchNodeMapper nodeMapper;
    private final ProductionDispatchMapper dispatchMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final ObjectMapper OM = new ObjectMapper();

    /** 责任历史稳定排序：assignedAt → createTime → nodeId */
    private static final Comparator<ProductionDispatchNode> HISTORY_ORDER =
            Comparator.comparing(ProductionDispatchNode::getAssignedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ProductionDispatchNode::getCreateTime,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ProductionDispatchNode::getNodeId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public boolean hasNodes(Long dispatchId) {
        Long cnt = nodeMapper.selectCount(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatchId));
        return cnt != null && cnt > 0;
    }

    @Override
    public List<DispatchNodeVO> getResponsibilityChain(Long dispatchId) {
        // P1-E cutover：Node = 唯一责任读取 Source of Truth。
        // 无 Node 的 dispatch = migration/data integrity anomaly（不再 fallback operators）。
        List<ProductionDispatchNode> nodes = nodeMapper.selectList(
                Wrappers.<ProductionDispatchNode>lambdaQuery()
                        .eq(ProductionDispatchNode::getDispatchId, dispatchId));
        if (nodes.isEmpty()) {
            log.warn("[CUTOVER] dispatchId={} 无 Node：migration/data integrity anomaly（不再 fallback operators）", dispatchId);
            return new ArrayList<>();
        }
        return nodes.stream()
                .sorted(HISTORY_ORDER)
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public DispatchNodeVO getCurrentActiveNode(Long dispatchId) {
        // P1-E cutover：唯一 ACTIVE 节点；无 Node → 无当前责任人（anomaly 已记录）
        ProductionDispatchNode active = nodeMapper.selectOne(
                Wrappers.<ProductionDispatchNode>lambdaQuery()
                        .eq(ProductionDispatchNode::getDispatchId, dispatchId)
                        .eq(ProductionDispatchNode::getNodeStatus, DispatchNodeStatusEnum.ACTIVE.getCode())
                        .last("LIMIT 1"));
        if (active == null) {
            // 有 dispatch 无 ACTIVE（正常：整单退回/已完成）与无 Node（异常）区分：
            // 无 Node 时记录 anomaly；无 ACTIVE 但有 Node 是正常业务态
            if (!hasNodes(dispatchId)) {
                log.warn("[CUTOVER] dispatchId={} 无 Node：migration/data integrity anomaly", dispatchId);
            }
            return null;
        }
        return toVO(active);
    }

    @Override
    public boolean isCurrentAssignee(Long dispatchId, Long userId) {
        if (userId == null) return false;
        Long cnt = nodeMapper.selectCount(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, dispatchId)
                .eq(ProductionDispatchNode::getNodeStatus, DispatchNodeStatusEnum.ACTIVE.getCode())
                .eq(ProductionDispatchNode::getAssigneeId, userId));
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean hasUserParticipated(Long userId) {
        if (userId == null) return false;
        // P1-E cutover：只读 Node（不再 legacy LIKE）
        Long nodeCnt = nodeMapper.selectCount(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getAssigneeId, userId));
        return nodeCnt != null && nodeCnt > 0;
    }

    @Override
    public DispatchNodeComparisonVO compareNodeAndLegacy(Long dispatchId) {
        DispatchNodeComparisonVO vo = new DispatchNodeComparisonVO();
        vo.setDispatchId(dispatchId);
        ProductionDispatch d = dispatchMapper.selectById(dispatchId);
        if (d == null) {
            vo.setResult("EMPTY");
            vo.setDetail("dispatch 不存在");
            return vo;
        }
        boolean hasNode = hasNodes(dispatchId);
        List<Long> nodeIds = hasNode ? nodeMapper.selectList(
                        Wrappers.<ProductionDispatchNode>lambdaQuery()
                                .eq(ProductionDispatchNode::getDispatchId, dispatchId))
                .stream().sorted(HISTORY_ORDER)
                .map(ProductionDispatchNode::getAssigneeId).collect(Collectors.toList())
                : new ArrayList<>();
        List<Long> legacyIds = legacyAssigneeIds(d.getOperators());

        vo.setNodeAssigneeIds(nodeIds);
        vo.setLegacyAssigneeIds(legacyIds);
        vo.setNodeAssigneeCount(nodeIds.size());
        vo.setLegacyAssigneeCount(legacyIds.size());

        if (!hasNode && legacyIds.isEmpty()) {
            vo.setResult("EMPTY");
            vo.setDetail("无 Node 且 legacy operators 为空");
        } else if (!hasNode) {
            vo.setResult("LEGACY_ONLY");
            vo.setDetail("仅 legacy operators（尚未 backfill，P1-E 处理）");
        } else if (legacyIds.isEmpty()) {
            vo.setResult("NODE_ONLY");
            vo.setDetail("仅 Node（legacy operators 为空，Node 为准）");
        } else if (nodeIds.equals(legacyIds)) {
            vo.setResult("MATCH");
            vo.setDetail("Node 责任链与 legacy operators 一致");
        } else {
            vo.setResult("MISMATCH");
            vo.setDetail("Node 责任链与 legacy operators 不一致（Node 为准）");
        }
        return vo;
    }

    // ==================== helpers ====================

    private DispatchNodeVO toVO(ProductionDispatchNode n) {
        DispatchNodeVO vo = new DispatchNodeVO();
        vo.setNodeId(n.getNodeId());
        vo.setDispatchId(n.getDispatchId());
        vo.setParentNodeId(n.getParentNodeId());
        vo.setAssigneeType(n.getAssigneeType());
        vo.setAssigneeId(n.getAssigneeId());
        vo.setAssigneeName(n.getAssigneeName());
        vo.setOrgId(n.getOrgId());
        vo.setOrgName(n.getOrgName());
        vo.setNodeStatus(n.getNodeStatus());
        vo.setAssignedBy(n.getAssignedBy());
        vo.setAssignedByName(n.getAssignedByName());
        vo.setAssignedAt(n.getAssignedAt());
        vo.setClosedAt(n.getClosedAt());
        vo.setRemark(n.getRemark());
        vo.setSource("NODE");
        return vo;
    }

    /** 解析 legacy operators 的 userId 顺序列表（仅 compareNodeAndLegacy 诊断用） */
    private List<Long> legacyAssigneeIds(String operatorsJson) {
        List<Long> ids = new ArrayList<>();
        if (operatorsJson == null || operatorsJson.isBlank()) return ids;
        try {
            JsonNode arr = OM.readTree(operatorsJson);
            if (arr == null || !arr.isArray()) return ids;
            arr.forEach(n -> {
                long uid = n.path("userId").asLong(0);
                if (uid != 0) ids.add(uid);
            });
        } catch (Exception e) {
            log.warn("解析 legacy operators 失败: {}", e.getMessage());
        }
        return ids;
    }

    private String legacyNameOf(String operatorsJson, int index) {
        try {
            JsonNode arr = OM.readTree(operatorsJson);
            if (arr != null && arr.isArray() && index < arr.size()) {
                return arr.get(index).path("userName").asText("");
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
