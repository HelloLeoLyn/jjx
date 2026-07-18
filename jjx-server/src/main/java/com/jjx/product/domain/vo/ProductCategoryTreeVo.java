package com.jjx.product.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 产品分类树VO
 */
@Data
public class ProductCategoryTreeVo {

    /** 分类ID */
    private Long categoryId;

    /** 分类编码 */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 父分类ID */
    private Long parentId;

    /** 层级 */
    private Integer categoryLevel;

    /** 排序 */
    private Integer sortOrder;

    /** 状态 */
    private String status;

    /** 子分类列表 */
    private List<ProductCategoryTreeVo> children;
}
