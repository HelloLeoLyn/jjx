package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工序记录更新DTO
 */
@Data
@Schema(description = "生产工序记录更新DTO")
public class ProductionOperationRecordUpdateDTO {

    @Schema(description = "记录ID", required = true, example = "1")
    @NotNull(message = "记录ID不能为空")
    private Long recordId;

    @Schema(description = "记录类型：START开始/PAUSE暂停/RESUME恢复/COMPLETE完成/QUALITY_CHECK质量检查/EQUIPMENT_CHANGE设备更换/MATERIAL_CHANGE物料更换/PARAMETER_ADJUST参数调整/EXCEPTION异常/RESUME_AFTER_EXCEPTION异常恢复/OTHER其他", example = "COMPLETE")
    private String recordType;

    @Schema(description = "记录时间", example = "2024-04-12 12:00:00")
    private LocalDateTime recordTime;

    @Schema(description = "操作员ID", example = "1")
    private Long operatorId;

    @Schema(description = "操作员姓名", example = "张三")
    private String operatorName;

    @Schema(description = "累计完成数量", example = "100")
    private BigDecimal cumulativeCompletedQuantity;

    @Schema(description = "累计合格数量", example = "96")
    private BigDecimal cumulativeQualifiedQuantity;

    @Schema(description = "累计不良数量", example = "4")
    private BigDecimal cumulativeDefectiveQuantity;

    @Schema(description = "累计人工工时（小时）", example = "2.5")
    private BigDecimal cumulativeLaborHours;

    @Schema(description = "累计机器工时（小时）", example = "1.5")
    private BigDecimal cumulativeMachineHours;

    @Schema(description = "设备ID", example = "1")
    private Long equipmentId;

    @Schema(description = "设备编码", example = "EQ-001")
    private String equipmentCode;

    @Schema(description = "设备名称", example = "CNC机床")
    private String equipmentName;

    @Schema(description = "工艺参数JSON", example = "{\"speed\": 1200, \"feed\": 0.15}")
    private String processParameters;

    @Schema(description = "质量状态：PENDING待检/QUALIFIED合格/DEFECTIVE不良/REWORK返修/SCRAP报废", example = "QUALIFIED")
    private String qualityStatus;

    @Schema(description = "质量检查人ID", example = "2")
    private Long qualityInspectorId;

    @Schema(description = "质量检查人姓名", example = "李四")
    private String qualityInspectorName;

    @Schema(description = "质量检查时间", example = "2024-04-12 11:50:00")
    private LocalDateTime qualityInspectionTime;

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

    @Schema(description = "物料批次号", example = "BATCH20240411001")
    private String materialBatchNo;

    @Schema(description = "备注", example = "生产完成")
    private String remark;

    @Schema(description = "附件URL", example = "/attachments/record_001.pdf")
    private String attachmentUrl;
}
