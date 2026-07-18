package com.jjx.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 客户修改DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "客户修改DTO")
public class CustomerEditDTO extends CustomerAddDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long customerId;

    @Size(max = 50, message = "客户编码长度不能超过50个字符")
    @Schema(description = "客户编码", example = "CUST001")
    private String customerCode;
}
