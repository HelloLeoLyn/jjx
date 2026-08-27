package com.jjx.product.domain.dto;

import lombok.Data;

@Data
public class ProductUpdateDTO {
    private Long productId;
    private String productName;
    private String approveRemark;
    private Integer targetStatus;
    private Integer currentStatus;
}
