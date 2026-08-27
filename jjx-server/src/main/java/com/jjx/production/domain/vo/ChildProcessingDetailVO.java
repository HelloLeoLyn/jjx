package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "当前用户在某工序下的直接Child处理明细")
public class ChildProcessingDetailVO {
    private Long executionId;
    private String orderNo;
    private String processName;
    private Integer executionStatus;
    private BigDecimal myResponsibilityQuantity;
    private BigDecimal childCompletedQuantity;
    private BigDecimal childProcessingQuantity;
    private BigDecimal pendingMyApprovalQuantity;
    private List<Record> records = new ArrayList<>();

    @Data
    public static class Record {
        private Long taskId;
        private String taskNo;
        private Long assigneeId;
        private String assigneeName;
        private String departmentName;
        private BigDecimal taskQuantity;
        private BigDecimal completedQuantity;
        private BigDecimal pendingApprovalQuantity;
        private BigDecimal processingQuantity;
        private String status;
        private String statusLabel;
    }
}
