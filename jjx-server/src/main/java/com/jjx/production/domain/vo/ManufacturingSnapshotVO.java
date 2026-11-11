package com.jjx.production.domain.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 工单制造历史快照（dev-20261111-009 G1）。
 *
 * <p>全部按工单持久化 ID 组装：BOM/路线/规范/物料任一档案后续变化都不应改变本快照；
 * 任一层缺失时返回 SNAPSHOT_PARTIAL，不用“最新版本”顶替。</p>
 */
@Data
public class ManufacturingSnapshotVO {

    private Long orderId;
    private String orderNo;
    private String outputType;
    private Long outputId;
    private String productCode;
    private String productName;
    private BigDecimal plannedQuantity;

    private Long bomId;
    private String bomCode;
    private Long routingId;
    private String routingCode;
    private String routingVersion;
    private Long workSpecVersionId;
    private String workSpecVersionNo;

    /** COMPLETE / SNAPSHOT_PARTIAL */
    private String snapshotStatus;
    private Integer depth;

    private List<ManufacturingSnapshotNodeVO> nodes = new ArrayList<>();
    /** 路线工序行（历史路线依据）。 */
    private List<RoutingStep> routingSteps = new ArrayList<>();
    /** 工单实际工序快照（production_operation_execution）。 */
    private List<ExecutionStep> executionSteps = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();
    private List<String> blockers = new ArrayList<>();

    @Data
    public static class RoutingStep {
        private Integer processOrder;
        private String processName;
        private Long processId;
        private Integer workflowSeq;
        private String workInstruction;
    }

    @Data
    public static class ExecutionStep {
        private Long executionId;
        private Integer processOrder;
        private String processName;
        private Integer executionStatus;
    }
}
