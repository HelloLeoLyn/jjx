package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * 产品配置结果VO
 */
@Data
public class ProductConfigResult {
    /** 产品ID */
    private Long productId;

    /** BOM ID */
    private Long bomId;

    /** 工艺路线ID */
    private Long routeId;

    /** 是否成功 */
    private Boolean success;
}
