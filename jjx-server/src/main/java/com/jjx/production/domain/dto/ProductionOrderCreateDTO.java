package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产订单创建DTO
 * 用于创建生产计划或生产工单
 */
@Data
@Schema(description = "生产订单创建DTO")
public class ProductionOrderCreateDTO {
    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "PO20160901001")
    @NotBlank(message = "订单编号不能为空")
    private String traceId;
    private String orderNo;

    @Schema(description = "订单类型：PLAN生产计划/WORK_ORDER生产工单", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLAN")
    @NotBlank(message = "订单类型不能为空")
    private String orderType;

    @Schema(description = "父订单ID（计划生成工单时使用）", example = "1")
    private Long parentOrderId;

    @Schema(description = "销售订单ID", example = "1")
    private Long salesOrderId;

    @Schema(description = "销售订单编号", example = "SO20240411001")
    @Size(max = 50, message = "销售订单编号长度不能超过50个字符")
    private String salesOrderNo;

    @Schema(description = "产品ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "产品ID不能为空")
    private Long productId;

    @Schema(description = "产品编码", requiredMode = Schema.RequiredMode.AUTO, example = "P001")
    @NotBlank(message = "产品编码不能为空")
    @Size(max = 50, message = "产品编码长度不能超过50个字符")
    private String productCode;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "薄膜开关")
    @NotBlank(message = "产品名称不能为空")
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
    private Long bomId;
    private String bomCode;

    @Schema(description = "工艺路线编码", example = "ROUTE-001")
    @Size(max = 50, message = "工艺路线编码长度不能超过50个字符")
    private String routingCode;

    @Schema(description = "计划数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "计划数量不能为空")
    private BigDecimal plannedQuantity;

    @Schema(description = "计划开始日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-04-12")
    @NotNull(message = "计划开始日期不能为空")
    private LocalDate planStartDate;

    @Schema(description = "计划结束日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-04-20")
    @NotNull(message = "计划结束日期不能为空")
    private LocalDate planEndDate;

    @Schema(description = "优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急", required = true, example = "MEDIUM")
    @NotBlank(message = "优先级不能为空")
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
