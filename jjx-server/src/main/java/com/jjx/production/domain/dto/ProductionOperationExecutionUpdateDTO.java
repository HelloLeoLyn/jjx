package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序执行更新DTO
 */
@Data
@Schema(description = "生产工序执行更新DTO")
public class ProductionOperationExecutionUpdateDTO {

    @Schema(description = "执行ID", required = true, example = "1")
    @NotNull(message = "执行ID不能为空")
    private Long executionId;

    @Schema(description = "实际开始时间", example = "2024-04-12 08:15:00")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际结束时间", example = "2024-04-12 11:45:00")
    private LocalDateTime actualEndTime;

    @Schema(description = "实际人工工时（小时）", example = "2.3")
    private BigDecimal actualLaborHours;

    @Schema(description = "实际机器工时（小时）", example = "1.4")
    private BigDecimal actualMachineHours;

    @Schema(description = "实际准备时间（小时）", example = "0.4")
    private BigDecimal actualSetupTime;

    @Schema(description = "实际清理时间（小时）", example = "0.2")
    private BigDecimal actualCleanupTime;

    @Schema(description = "实际完成数量", example = "98")
    private BigDecimal actualCompletedQuantity;

    @Schema(description = "实际合格数量", example = "95")
    private BigDecimal actualQualifiedQuantity;

    @Schema(description = "实际不良数量", example = "3")
    private BigDecimal actualDefectiveQuantity;

    @Schema(description = "实际合格率（百分比）", example = "96.9")
    private BigDecimal actualQualifiedRate;

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

    @Schema(description = "质量状态：PENDING待检/QUALIFIED合格/DEFECTIVE不良/REWORK返修/SCRAP报废", example = "QUALIFIED")
    private String qualityStatus;

    @Schema(description = "质量检查人ID", example = "2")
    private Long qualityInspectorId;

    @Schema(description = "质量检查人姓名", example = "李四")
    private String qualityInspectorName;

    @Schema(description = "质量检查时间", example = "2024-04-12 11:50:00")
    private LocalDateTime qualityInspectionTime;

    @Schema(description = "质量备注", example = "表面光滑，符合要求")
    private String qualityRemark;

    @Schema(description = "异常代码", example = "EQ001")
    private String exceptionCode;

    @Schema(description = "异常描述", example = "设备短暂停机")
    private String exceptionDescription;

    @Schema(description = "异常处理人ID", example = "3")
    private Long exceptionHandlerId;

    @Schema(description = "异常处理人姓名", example = "王五")
    private String exceptionHandlerName;

    @Schema(description = "异常处理时间", example = "2024-04-12 09:30:00")
    private LocalDateTime exceptionHandleTime;

    @Schema(description = "异常处理结果", example = "已修复，恢复正常")
    private String exceptionHandleResult;

    /** 不良原因（P0-03：与 remark 分离，正确映射 defective_reason） */
    private String defectiveReason;

    /** 备注（P0-03：execution 实体无 remark 字段，不持久化，不再误写入 defective_reason） */
    private String remark;
}
