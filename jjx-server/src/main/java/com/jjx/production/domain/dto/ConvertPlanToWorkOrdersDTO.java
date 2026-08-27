package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 计划转工单DTO
 */
@Data
@Schema(description = "计划转工单DTO")
public class ConvertPlanToWorkOrdersDTO {

    @Schema(description = "生产计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "计划ID不能为空")
    private Long planId;

    @Schema(description = "待转换的工单列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "工单列表不能为空")
    @Valid
    private List<WorkOrderItem> workOrders;

    @Schema(description = "是否批量转换")
    private Boolean batchConvert;

    @Data
    @Schema(description = "工单项")
    public static class WorkOrderItem {

        @Schema(description = "产品ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "产品ID不能为空")
        private Long productId;

        @Schema(description = "产品编码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "产品编码不能为空")
        private String productCode;

        @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "产品名称不能为空")
        private String productName;

        @Schema(description = "计划数量", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "计划数量不能为空")
        private BigDecimal plannedQuantity;

        @Schema(description = "计划开始日期", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "计划开始日期不能为空")
        private LocalDate planStartDate;

        @Schema(description = "计划结束日期", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "计划结束日期不能为空")
        private LocalDate planEndDate;

        @Schema(description = "优先级", example = "MEDIUM")
        private String priority;

        @Schema(description = "备注")
        private String remark;
    }
}
