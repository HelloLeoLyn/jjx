package com.jjx.product.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 产品价格维护只返回本页面需要的产品标识与价格。 */
@Data
public class ProductPriceVO {
    private Long productId;
    private String productCode;
    private String productName;
    private String customerName;
    private String unit;
    private BigDecimal basePrice;
    private BigDecimal costPrice;
}
