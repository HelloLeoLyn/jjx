package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产品配置工艺路线DTO
 */
@Data
public class ProductConfigRouteDTO {
    /** 产品ID */
    @NotNull(message = "产品id不能为空")
    private Long productId;

    /** 工艺路线ID */
    @NotNull(message = "工艺路线id不能为空")
    private Long currentRouteId;
}
