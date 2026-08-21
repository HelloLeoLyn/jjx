package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 我的任务节点 VO（P3：工序执行页「我的当前任务/我已完成」）
 * 数量全部动态汇总（selfReported 从 WorkReport 汇总；childOccupied = Σ 直接子节点 effective）
 */
@Data
@Schema(description = "我的任务节点VO")
public class MyTaskNodeVO {

    private Long taskNodeId;
    private Long executionId;
    private Long parentNodeId;
    private Long assigneeId;
    private String assigneeName;
    private BigDecimal taskQuantity;
    private BigDecimal recalledQuantity;
    private BigDecimal selfReported;
    private BigDecimal childOccupied;
    private BigDecimal selfRemaining;
    private BigDecimal availableToAssign;
    private String status;
    private String statusLabel;

    private Long orderId;
    private String orderNo;
    private String processName;
    private Integer processOrder;
    private Integer executionStatus;
    private String executionStatusDesc;
    private BigDecimal executionInputQuantity;
}
