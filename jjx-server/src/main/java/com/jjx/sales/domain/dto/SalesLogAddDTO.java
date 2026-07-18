package com.jjx.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
@Schema(description = "添加操作日志DTO")
public class SalesLogAddDTO {
    
    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;
    
    @NotBlank(message = "操作类型不能为空")
    @Schema(description = "操作类型", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"create", "update", "pay", "ship", "cancel", "approve", "reject", "submit_review", "complete"})
    private String operationType;
    
    @Schema(description = "操作描述")
    private String operationDescription;
    
    @Schema(description = "备注")
    private String remark;
}