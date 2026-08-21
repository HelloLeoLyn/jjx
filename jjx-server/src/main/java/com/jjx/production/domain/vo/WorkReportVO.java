package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工 VO（P2-B：Execution 页面报工历史展示用）
 */
@Data
public class WorkReportVO {

    private Long reportId;

    private Long orderId;
    private String orderNo;

    private Long executionId;

    private Long taskNodeId;

    private Long reporterId;
    private String reporterName;

    private Long equipmentId;
    private String equipmentName;

    private BigDecimal qualifiedQuantity;
    private BigDecimal defectiveQuantity;

    private BigDecimal laborHours;
    private BigDecimal machineHours;

    private LocalDateTime workStartTime;
    private LocalDateTime workEndTime;
    private LocalDateTime reportTime;

    private String defectReason;
    private String remark;

    private String reportStatus;
    private String reportStatusLabel;

    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private String cancelReason;
}
