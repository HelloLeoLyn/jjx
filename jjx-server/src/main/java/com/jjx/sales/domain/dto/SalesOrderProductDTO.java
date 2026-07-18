package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.annotation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单产品明细添加DTO
 */
@Data
@Schema(description = "订单产品明细添加DTO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesOrderProductDTO {
    @NotNull(message = "产品数量不能为空")
    @Min(value = 1, message = "产品数量至少为1")
    @Schema(description = "产品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer quantity;

    @NotNull(message = "产品金额不能为空")
    @DecimalMin(value = "0", message = "产品金额不能为负数")
    @Schema(description = "产品金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "订单ID不能为空", groups = ValidationGroups.Update.class)
    private Long orderId;

    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2001")
    private Long productId;

    @Schema(description = "单位", example = "PCS")
    @NotNull(message = "单位不能为空")
    private String unit;

    @DecimalMin(value = "0", message = "单价不能为负数")
    @Schema(description = "单价", example = "100.00")
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    @NotBlank(message = "产品编码不能为空")
    @Size(max = 20, message = "产品编码长度不能超过20个字符")
    @Schema(description = "产品编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "P2024001")
    private String productCode;

    @NotBlank(message = "产品名称不能为空")
    @Size(max = 20, message = "产品名称长度不能超过20个字符")
    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "薄膜开关")
    private String productName;

}
