package com.jjx.product.domain.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品分类查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCategoryQuery extends PageQuery {

    /** 分类编码 */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 父分类ID */
    private Long parentId;

    /** 状态 */
    private String status;
}
