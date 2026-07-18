package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生产工序记录查询DTO
 */
@Data
@Schema(description = "生产工序记录查询DTO")
public class ProductionOperationRecordQueryDTO {

    @Schema(description = "工序执行ID", example = "1")
    private Long executionId;

    @Schema(description = "生产订单ID", example = "1")
    private Long orderId;

    @Schema(description = "生产订单编号", example = "PL20240411001")
    private String orderNo;

    @Schema(description = "记录类型：START开始/PAUSE暂停/RESUME恢复/COMPLETE完成/QUALITY_CHECK质量检查/EQUIPMENT_CHANGE设备更换/MATERIAL_CHANGE物料更换/PARAMETER_ADJUST参数调整/EXCEPTION异常/RESUME_AFTER_EXCEPTION异常恢复/OTHER其他", example = "START")
    private String recordType;

    @Schema(description = "操作员ID", example = "1")
    private Long operatorId;

    @Schema(description = "操作员姓名", example = "张三")
    private String operatorName;

    @Schema(description = "设备ID", example = "1")
    private Long equipmentId;

    @Schema(description = "设备编码", example = "EQ-001")
    private String equipmentCode;

    @Schema(description = "质量状态：PENDING待检/QUALIFIED合格/DEFECTIVE不良/REWORK返修/SCRAP报废", example = "QUALIFIED")
    private String qualityStatus;

    @Schema(description = "是否有异常", example = "true")
    private Boolean hasException;

    @Schema(description = "记录时间-开始", example = "2024-04-01")
    private LocalDate recordTimeFrom;

    @Schema(description = "记录时间-结束", example = "2024-04-30")
    private LocalDate recordTimeTo;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "排序字段", example = "recordTime")
    private String orderBy = "recordTime";

    @Schema(description = "排序方向", example = "desc")
    private String orderDirection = "desc";
}
