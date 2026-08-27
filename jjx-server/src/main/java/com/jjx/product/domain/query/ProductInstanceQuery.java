package com.jjx.product.domain.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品实例查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductInstanceQuery extends PageQuery {

    /** 实例编码 */
    private String instanceCode;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;
    /**
     * 状态：pending待生产/in_production生产中/completed已完成/delivered已交付
     */
    private Integer instanceStatus;
    /** 产品名称 */
    private String productName;

    /** 订单ID */
    private Long orderId;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 生产状态 */
    private String productionStatus;

    /** 开始时间范围 - 开始 */
    private String startTimeBegin;

    /** 开始时间范围 - 结束 */
    private String startTimeEnd;

    /** 完成时间范围 - 开始 */
    private String completeTimeBegin;

    /** 完成时间范围 - 结束 */
    private String completeTimeEnd;
}
