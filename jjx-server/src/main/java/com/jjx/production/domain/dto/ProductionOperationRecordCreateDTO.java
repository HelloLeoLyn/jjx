package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序记录创建DTO
 */
@Data
@Schema(description = "生产工序记录创建DTO")
public class ProductionOperationRecordCreateDTO {

    @Schema(description = "工序执行ID", required = true, example = "1")
    @NotNull(message = "工序执行ID不能为空")
    private Long executionId;

    @Schema(description = "记录类型：START开始/PAUSE暂停/RESUME恢复/COMPLETE完成/QUALITY_CHECK质量检查/EQUIPMENT_CHANGE设备更换/MATERIAL_CHANGE物料更换/PARAMETER_ADJUST参数调整/EXCEPTION异常/RESUME_AFTER_EXCEPTION异常恢复/OTHER其他", required = true, example = "START")
    @NotNull(message = "记录类型不能为空")
    private String recordType;

    @Schema(description = "记录时间", required = true, example = "2024-04-12 08:00:00")
    @NotNull(message = "记录时间不能为空")
    private LocalDateTime recordTime;

    @Schema(description = "操作员ID", required = true, example = "1")
    @NotNull(message = "操作员ID不能为空")
    private Long operatorId;

    @Schema(description = "操作员姓名", required = true, example = "张三")
    @NotNull(message = "操作员姓名不能为空")
    private String operatorName;

    @Schema(description = "累计完成数量", example = "50")
    private BigDecimal cumulativeCompletedQuantity;

    @Schema(description = "累计合格数量", example = "48")
    private BigDecimal cumulativeQualifiedQuantity;

    @Schema(description = "累计不良数量", example = "2")
    private BigDecimal cumulativeDefectiveQuantity;

    @Schema(description = "累计人工工时（小时）", example = "1.2")
    private BigDecimal cumulativeLaborHours;

    @Schema(description = "累计机器工时（小时）", example = "0.8")
    private BigDecimal cumulativeMachineHours;

    @Schema(description = "设备ID", example = "1")
    private Long equipmentId;

    @Schema(description = "设备编码", example = "EQ-001")
    private String equipmentCode;

    @Schema(description = "设备名称", example = "CNC机床")
    private String equipmentName;

    @Schema(description = "工艺参数JSON", example = "{\"speed\": 1000, \"feed\": 0.1}")
    private String processParameters;

    @Schema(description = "质量状态：PENDING待检/QUALIFIED合格/DEFECTIVE不良/REWORK返修/SCRAP报废", example = "QUALIFIED")
    private String qualityStatus;

    @Schema(description = "质量检查人ID", example = "2")
    private Long qualityInspectorId;

    @Schema(description = "质量检查人姓名", example = "李四")
    private String qualityInspectorName;

    @Schema(description = "异常代码", example = "EQ001")
    private String exceptionCode;

    @Schema(description = "异常描述", example = "设备短暂停机")
    private String exceptionDescription;

    @Schema(description = "物料批次号", example = "BATCH20240411001")
    private String materialBatchNo;

    @Schema(description = "备注", example = "开始生产")
    private String remark;

    @Schema(description = "附件URL", example = "/attachments/record_001.pdf")
    private String attachmentUrl;
}
