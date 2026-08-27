package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "当前用户按工序聚合的生产责任")
public class MyProductionExecutionVO {
    private Long executionId;
    private Long orderId;
    private String orderNo;
    private Integer orderStatus;
    private Long processId;
    private String processName;
    private Integer processOrder;
    private Integer executionStatus;
    private LocalDateTime actualStartTime;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private Integer taskCount;
    private String taskNo;
    private BigDecimal plannedQuantity;
    private BigDecimal myResponsibilityQuantity;
    private BigDecimal myCompletedQuantity;
    private BigDecimal myPendingReviewQuantity;
    private BigDecimal myProcessableQuantity;
    private BigDecimal childCompletedQuantity;
    private BigDecimal childProcessingQuantity;
    private BigDecimal pendingMyApprovalQuantity;
}
