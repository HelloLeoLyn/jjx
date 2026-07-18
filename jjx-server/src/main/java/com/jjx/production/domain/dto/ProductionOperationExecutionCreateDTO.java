package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序执行创建DTO
 */
@Data
@Schema(description = "生产工序执行创建DTO")
public class ProductionOperationExecutionCreateDTO {

    @Schema(description = "生产订单ID", required = true, example = "1")
    @NotNull(message = "生产订单ID不能为空")
    private Long orderId;

    @Schema(description = "工序ID", required = true, example = "1")
    @NotNull(message = "工序ID不能为空")
    private Long processId;

    @Schema(description = "工序编码", required = true, example = "PROC-001")
    @NotNull(message = "工序编码不能为空")
    private String processCode;

    @Schema(description = "工序名称", required = true, example = "机加工")
    @NotNull(message = "工序名称不能为空")
    private String processName;

    @Schema(description = "工序顺序", required = true, example = "1")
    @NotNull(message = "工序顺序不能为空")
    private Integer processOrder;

    @Schema(description = "计划开始时间", required = true, example = "2024-04-12 08:00:00")
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime planStartTime;

    @Schema(description = "计划结束时间", required = true, example = "2024-04-12 12:00:00")
    @NotNull(message = "计划结束时间不能为空")
    private LocalDateTime planEndTime;

    @Schema(description = "计划数量", required = true, example = "100")
    @NotNull(message = "计划数量不能为空")
    private BigDecimal plannedQuantity;

    @Schema(description = "标准人工工时（小时）", example = "2.5")
    private BigDecimal standardLaborHours;

    @Schema(description = "标准机器工时（小时）", example = "1.5")
    private BigDecimal standardMachineHours;

    @Schema(description = "标准准备时间（小时）", example = "0.5")
    private BigDecimal standardSetupTime;

    @Schema(description = "标准清理时间（小时）", example = "0.3")
    private BigDecimal standardCleanupTime;

    @Schema(description = "设备ID", example = "1")
    private Long equipmentId;

    @Schema(description = "设备编码", example = "EQ-001")
    private String equipmentCode;

    @Schema(description = "设备名称", example = "CNC机床")
    private String equipmentName;

    @Schema(description = "操作员ID", example = "1")
    private Long operatorId;

    @Schema(description = "操作员姓名", example = "张三")
    private String operatorName;

    @Schema(description = "执行状态：NOT_STARTED未开始/IN_PROGRESS进行中/PAUSED已暂停/COMPLETED已完成/CANCELLED已取消", example = "NOT_STARTED")
    private String executionStatus = "NOT_STARTED";

    @Schema(description = "备注", example = "重要工序，注意质量")
    private String remark;
}
