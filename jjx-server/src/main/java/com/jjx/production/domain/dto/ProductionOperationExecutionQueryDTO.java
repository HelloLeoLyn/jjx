package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生产工序执行查询DTO
 */
@Data
@Schema(description = "生产工序执行查询DTO")
public class ProductionOperationExecutionQueryDTO {

    @Schema(description = "生产订单ID", example = "1")
    private Long orderId;

    @Schema(description = "生产订单编号", example = "PL20240411001")
    private String orderNo;

    @Schema(description = "工序ID", example = "1")
    private Long processId;

    @Schema(description = "工序编码", example = "PROC-001")
    private String processCode;

    @Schema(description = "工序名称", example = "机加工")
    private String processName;

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

    @Schema(description = "执行状态：NOT_STARTED未开始/IN_PROGRESS进行中/PAUSED已暂停/COMPLETED已完成/CANCELLED已取消", example = "IN_PROGRESS")
    private String executionStatus;

    @Schema(description = "质量状态：PENDING待检/QUALIFIED合格/DEFECTIVE不良/REWORK返修/SCRAP报废", example = "QUALIFIED")
    private String qualityStatus;

    @Schema(description = "计划开始时间-开始", example = "2024-04-01")
    private LocalDate planStartTimeFrom;

    @Schema(description = "计划开始时间-结束", example = "2024-04-30")
    private LocalDate planStartTimeTo;

    @Schema(description = "计划结束时间-开始", example = "2024-04-01")
    private LocalDate planEndTimeFrom;

    @Schema(description = "计划结束时间-结束", example = "2024-04-30")
    private LocalDate planEndTimeTo;

    @Schema(description = "实际开始时间-开始", example = "2024-04-01")
    private LocalDate actualStartTimeFrom;

    @Schema(description = "实际开始时间-结束", example = "2024-04-30")
    private LocalDate actualStartTimeTo;

    @Schema(description = "实际结束时间-开始", example = "2024-04-01")
    private LocalDate actualEndTimeFrom;

    @Schema(description = "实际结束时间-结束", example = "2024-04-30")
    private LocalDate actualEndTimeTo;

    @Schema(description = "是否有异常", example = "true")
    private Boolean hasException;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "排序字段", example = "planStartTime")
    private String orderBy = "planStartTime";

    @Schema(description = "排序方向", example = "asc")
    private String orderDirection = "asc";

    @Schema(description = "P2-D：数据范围。mine=我的当前任务（对应 dispatch 存在 ACTIVE Node 且 assignee=当前用户）；空=全部")
    private String scope;
}
