package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产订单更新DTO
 */
@Data
@Schema(description = "生产订单更新DTO")
public class ProductionOrderUpdateDTO {

    @Schema(description = "订单ID", required = true, example = "1")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "订单类型：PLAN生产计划/WORK_ORDER生产工单", example = "PLAN")
    private String orderType;

    @Schema(description = "销售订单ID", example = "1")
    private Long salesOrderId;

    @Schema(description = "销售订单编号", example = "SO20240411001")
    @Size(max = 50, message = "销售订单编号长度不能超过50个字符")
    private String salesOrderNo;

    @Schema(description = "产品ID", example = "1")
    private Long productId;

    @Schema(description = "产品编码", example = "P001")
    @Size(max = 50, message = "产品编码长度不能超过50个字符")
    private String productCode;

    @Schema(description = "产品名称", example = "薄膜开关")
    @Size(max = 100, message = "产品名称长度不能超过100个字符")
    private String productName;

    @Schema(description = "产品规格", example = "100x50mm")
    @Size(max = 200, message = "产品规格长度不能超过200个字符")
    private String productSpec;

    @Schema(description = "产品单位", example = "个")
    @Size(max = 20, message = "产品单位长度不能超过20个字符")
    private String productUnit;

    @Schema(description = "使用的工艺路线ID", example = "1")
    private Long routingId;

    @Schema(description = "工艺路线编码", example = "ROUTE-001")
    @Size(max = 50, message = "工艺路线编码长度不能超过50个字符")
    private String routingCode;

    @Schema(description = "计划数量", example = "1000")
    private BigDecimal plannedQuantity;

    @Schema(description = "已完成数量", example = "500")
    private BigDecimal completedQuantity;

    @Schema(description = "计划开始日期", example = "2024-04-12")
    private LocalDate planStartDate;

    @Schema(description = "计划结束日期", example = "2024-04-20")
    private LocalDate planEndDate;

    @Schema(description = "实际开始时间", example = "2024-04-12 08:00:00")
    private String actualStartTime;

    @Schema(description = "实际结束时间", example = "2024-04-20 17:00:00")
    private String actualEndTime;

    @Schema(description = "订单状态：0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期", example = "0")
    private Integer orderStatus;

    @Schema(description = "审批状态：PENDING待审批/APPROVED已批准/REJECTED已拒绝/CANCELLED已取消", example = "APPROVED")
    private String approvalStatus;

    @Schema(description = "审批人ID", example = "1")
    private Long approverId;

    @Schema(description = "审批人姓名", example = "张三")
    @Size(max = 50, message = "审批人姓名长度不能超过50个字符")
    private String approverName;

    @Schema(description = "审批备注", example = "符合生产要求")
    private String approvalRemark;

    @Schema(description = "优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急", example = "MEDIUM")
    private String priority;

    @Schema(description = "生产部门ID", example = "1")
    private Long departmentId;

    @Schema(description = "生产部门名称", example = "生产一部")
    @Size(max = 100, message = "生产部门名称长度不能超过100个字符")
    private String departmentName;

    @Schema(description = "材料成本", example = "5000.00")
    private BigDecimal materialCost;

    @Schema(description = "人工成本", example = "3000.00")
    private BigDecimal laborCost;

    @Schema(description = "总成本", example = "8000.00")
    private BigDecimal totalCost;

    @Schema(description = "备注", example = "紧急订单，请优先处理")
    private String remark;
}
