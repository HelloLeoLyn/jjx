package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产任务树节点 VO（递归树形结构）
 * remainingQuantity：节点剩余数量（effective - childOccupied - selfReported）
 * availableToAssign：可继续分配数量（= remainingQuantity，下限 0）
 * selfReported：本节点持有人的有效报工量（从 WorkReport 动态汇总）
 * status/statusLabel：动态投影（不落库）
 */
@Data
public class TaskNodeVO {

    private Long taskNodeId;
    private Long executionId;
    private Long parentNodeId;
    private Long assigneeId;
    private String assigneeName;
    /** 上级节点持有人姓名（任务来源展示；普通用户子树视图下上级不在树内，用于节点详情保留来源） */
    private String parentAssigneeName;
    private BigDecimal taskQuantity;
    private BigDecimal recalledQuantity;
    private BigDecimal selfReported;
    /** 已下分给直接子节点的有效数量（Σ 直接子节点 effective） */
    private BigDecimal childOccupied;
    private String status;
    private String statusLabel;
    private BigDecimal remainingQuantity;
    private BigDecimal availableToAssign;
    /** 是否有直接子节点（懒加载：决定展开箭头；由后端投影，不落库） */
    private Boolean hasChildren;
    private List<TaskNodeVO> children = new ArrayList<>();

    public static TaskNodeVO from(com.jjx.production.domain.entity.ProductionTaskNode n) {
        TaskNodeVO vo = new TaskNodeVO();
        vo.setTaskNodeId(n.getTaskNodeId());
        vo.setExecutionId(n.getExecutionId());
        vo.setParentNodeId(n.getParentNodeId());
        vo.setAssigneeId(n.getAssigneeId());
        vo.setAssigneeName(n.getAssigneeName());
        vo.setTaskQuantity(n.getTaskQuantity());
        vo.setRecalledQuantity(n.getRecalledQuantity());
        return vo;
    }
}
