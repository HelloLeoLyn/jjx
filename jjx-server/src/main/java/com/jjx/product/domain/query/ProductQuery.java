package com.jjx.product.domain.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 分类ID */
    private Long categoryId;

    /** 产品类型 */
    private String productType;

    /** 产品状态 */
    private String productStatus;

    /**
     * 开始日期
     */
    private String startDate;
    /**
     * 结束日期
     */
    private String endDate;
}
