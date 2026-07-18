package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产品配置DTO
 */
@Data
public class ProductConfigDTO {
    /** 产品ID */
    @NotNull(message = "产品id不能为空")
    private Long productId;

    /** BOM ID */
    @NotNull(message = "bomid不能为空")
    private Long currentBomId;
}
