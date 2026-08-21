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
    private BigDecimal taskQuantity;
    private BigDecimal recalledQuantity;
    private BigDecimal selfReported;
    /** 已下分给直接子节点的有效数量（Σ 直接子节点 effective） */
    private BigDecimal childOccupied;
    private String status;
    private String statusLabel;
    private BigDecimal remainingQuantity;
    private BigDecimal availableToAssign;
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
