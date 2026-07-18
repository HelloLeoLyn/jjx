package com.jjx.sales.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 订单查询参数 DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OrderQueryDTO extends PageQuery {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 订单状态
     */
    private Integer orderStatus;

    /**
     * 订单类型
     */
    private Integer orderType;

    /**
     * 支付状态
     */
    private Integer paymentStatus;

    /**
     * 生产状态
     */
    private Integer prodStatus;

    /**
     * 销售负责人ID
     */
    private Long salesManagerId;

    /**
     * 销售员姓名
     */
    private String salesPersonName;

    /**
     * 开始日期（订单日期范围）
     */
    private LocalDate startDate;

    /**
     * 结束日期（订单日期范围）
     */
    private LocalDate endDate;

    /**
     * 最小订单金额
     */
    private Double minAmount;

    /**
     * 最大订单金额
     */
    private Double maxAmount;

}
