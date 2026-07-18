package com.jjx.product.domain.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工艺路线查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductRouteQuery extends PageQuery {

    /** 路线编码 */
    private String routeCode;

    /** 路线名称 */
    private String routeName;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 版本号 */
    private String routeVersion;

    /** 是否当前版本 */
    private Boolean isCurrent;

    /** 审批状态 */
    private String approveStatus;
}
