package com.jjx.product.domain.vo;

import lombok.Data;

@Data
public class ProductCategoryVO {

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类编码
     */
    private String categoryCode;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 层级（1/2/3）
     */
    private Integer categoryLevel;


}
