package com.jjx.product.domain.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * BOM查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EngineeringBomQuery extends PageQuery {

    /** BOM编码 */
    private String bomCode;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** BOM类型 */
    private String bomType;

    /** 版本号 */
    private String bomVersion;

    /** 是否当前版本 */
    private Boolean isCurrent;

    /** 审批状态 */
    private String approveStatus;
}
