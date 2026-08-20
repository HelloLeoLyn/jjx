package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工序作业分配读取 VO（WP-B，供 WP-D 前端直接消费）
 */
@Data
public class AssignmentViewVO {

    /** 工序执行ID */
    private Long executionId;

    /** 工序计划数量（execution.input_quantity） */
    private BigDecimal plannedQuantity;

    /** 已分配数量（SUM ACTIVE effective） */
    private BigDecimal assignedQuantity;

    /** 已报工数量（SUM 有效 WorkReport output，跨 assignment） */
    private BigDecimal reportedQuantity;

    /** 未分配数量（planned - assigned） */
    private BigDecimal unassignedQuantity;

    /** 分配明细 */
    private List<AssignmentLineVO> assignments;

    @Data
    public static class AssignmentLineVO {
        private Long assignmentId;
        private Long executionId;
        private Long orderId;
        private Long dispatchId;
        private Long dispatchNodeId;

        private Long assigneeId;
        private String assigneeName;

        /** 原始分配数量（不可变） */
        private BigDecimal assignedQuantity;
        /** 已释放数量 */
        private BigDecimal releasedQuantity;
        /** 有效数量 = assigned - released */
        private BigDecimal effectiveQuantity;
        /** 已报工数量（有效 SUBMITTED qualified+defective） */
        private BigDecimal reportedQuantity;
        /** 剩余数量 = effective - reported */
        private BigDecimal remainingQuantity;

        /** 派生状态：remaining==0 → COMPLETED；否则 ACTIVE（行级 status 保留 CANCELLED） */
        private String derivedStatus;
        private String derivedStatusLabel;

        private String assignmentStatus;

        private Long assignedBy;
        private String assignedByName;
        private LocalDateTime assignedAt;

        private LocalDateTime cancelledAt;
        private String cancelReason;
    }
}
