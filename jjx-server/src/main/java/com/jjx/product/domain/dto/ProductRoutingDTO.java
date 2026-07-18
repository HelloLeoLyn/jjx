package com.jjx.product.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductRoutingDTO {

    private Long routingId;

    /**
     * 路线编码
     */
    private String routingCode;

    /**
     * 路线名称
     */
    private String routingName;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 版本号
     */
    private String routingVersion;

    /**
     * 路线说明
     */
    private String description;

    /**
     * 备注
     */
    private String remark;

    /**
     * 工序明细列表
     */
    private List<ProductRoutingItemDTO> items;
}
