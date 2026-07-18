package com.jjx.product.domain.vo;

import lombok.Data;

@Data
public class ProductValidationVO {
    private Long productId;
    private String productCode;
    private String productName;
    private Integer productStatus;
    private String productCategory;
    private Long bomId;
    private String bomCode;
    private String bomVersion;
    private Boolean isBomCurrentVersion;
    private Integer bomStatus;
    private Long routingId;
    private String routingCode;
    private String routingName;
    private Boolean isRoutingCurrentVersion;
    private String routingVersion;
    private Integer routingStatus;

}
