package com.jjx.product.domain.vo;

import lombok.Data;

/**
 * 产品实例VO
 */
@Data
public class ProductInstanceVo {

    /** 实例ID */
    private Long instanceId;

    /** 实例编码 */
    private String instanceCode;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 订单ID */
    private Long orderId;

    /** 订单明细ID */
    private Long orderItemId;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 定制规格参数（JSON字符串） */
    private String customSpecJson;

    /** 生产状态 */
    private String productionStatus;

    /** 开始生产时间 */
    private String startTime;

    /** 完成生产时间 */
    private String completeTime;

    /** 交付时间 */
    private String deliveryTime;

    /** 使用的BOM ID */
    private Long bomId;

    /** 使用的工艺路线ID */
    private Long routeId;

    /** 备注 */
    private String remark;
}
