package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生产订单查询DTO
 */
@Data
@Schema(description = "生产订单查询DTO")
public class ProductionOrderQueryDTO {

    @Schema(description = "订单编号", example = "PL20240411001")
    private String orderNo;

    @Schema(description = "订单类型：PLAN生产计划/WORK_ORDER生产工单", example = "PLAN")
    private String orderType;

    @Schema(description = "父订单ID", example = "1")
    private Long parentOrderId;

    @Schema(description = "销售订单ID", example = "1")
    private Long salesOrderId;

    @Schema(description = "销售订单编号", example = "SO20240411001")
    private String salesOrderNo;

    @Schema(description = "产品ID", example = "1")
    private Long productId;

    @Schema(description = "产品编码", example = "P001")
    private String productCode;

    @Schema(description = "产品名称", example = "薄膜开关")
    private String productName;

    @Schema(description = "使用的工艺路线ID", example = "1")
    private Long routingId;

    @Schema(description = "工艺路线编码", example = "ROUTE-001")
    private String routingCode;

    @Schema(description = "订单状态：0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期", example = "0")
    private Integer orderStatus;

    @Schema(description = "审批状态：PENDING待审批/APPROVED已批准/REJECTED已拒绝/CANCELLED已取消", example = "APPROVED")
    private Integer approvalStatus;

    @Schema(description = "优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急", example = "MEDIUM")
    private String priority;

    @Schema(description = "生产部门ID", example = "1")
    private Long departmentId;

    @Schema(description = "生产部门名称", example = "生产一部")
    private String departmentName;

    @Schema(description = "计划开始日期-开始", example = "2024-04-01")
    private LocalDate planStartDateFrom;

    @Schema(description = "计划开始日期-结束", example = "2024-04-30")
    private LocalDate planStartDateTo;

    @Schema(description = "计划结束日期-开始", example = "2024-04-01")
    private LocalDate planEndDateFrom;

    @Schema(description = "计划结束日期-结束", example = "2024-04-30")
    private LocalDate planEndDateTo;

    @Schema(description = "创建时间-开始", example = "2024-04-01")
    private LocalDate createTimeFrom;

    @Schema(description = "创建时间-结束", example = "2024-04-30")
    private LocalDate createTimeTo;

    @Schema(description = "是否超期", example = "true")
    private Boolean overdue;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "排序字段", example = "createTime")
    private String orderBy = "createTime";

    @Schema(description = "排序方向", example = "desc")
    private String orderDirection = "desc";
}
