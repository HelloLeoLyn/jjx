package com.jjx.product.domain.dto;

import lombok.Data;

/**
 * 产品标准工序查询DTO
 */
@Data
public class ProductStandardProcessQueryDTO {
    
    /**
     * 工序编码（模糊查询）
     */
    private String processCode;
    
    /**
     * 工序名称（模糊查询）
     */
    private String processName;
    
    /**
     * 工序类型
     */
    private String processType;
    
    /**
     * 工序类别
     */
    private String processCategory;
    
    /**
     * 是否启用
     */
    private Integer isEnabled;
    
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
     * 排序方式：asc/desc
     */
    private String isAsc = "desc";
}