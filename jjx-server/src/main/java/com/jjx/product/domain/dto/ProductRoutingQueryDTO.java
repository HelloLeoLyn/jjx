package com.jjx.product.domain.dto;

import lombok.Data;

@Data
public class ProductRoutingQueryDTO {
    
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
     * 审核状态
     */
    private Integer approveStatus;
    
    /**
     * 是否当前版本
     */
    private Integer isCurrent;
    
    /**
     * 页码
     */
    private Integer pageNum = 1;
    
    /**
     * 每页大小
     */
    private Integer pageSize = 10;
    
    /**
     * 排序字段
     */
    private String orderByColumn;
    
    /**
     * 排序方式
     */
    private String isAsc = "desc";
}