package com.jjx.purchase.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 采购订单查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PurchaseOrderQueryDTO extends PageQuery {

    /**
     * 采购订单号
     */
    private String orderNo;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 订单类型（normal正常/urgent紧急）
     */
    private String orderType;

    /**
     * 计划状态筛选（DEV-664）：0普通/1计划单
     */
    private Integer planStatus;

    /**
     * 订单审批状态（1草稿/2已取消/3待审批/4已批准/5已拒绝）
     */
    private Integer approvalStatus;

    /**
     * 收货状态（0待收货/1部分收货/2已收货）
     */
    private Integer receiptStatus;

    /**
     * 付款状态（0待付款/1部分付款/2已付款）
     */
    private Integer paymentStatus;

    /**
     * 是否紧急
     */
    private Boolean urgentFlag;

    /**
     * 开始订单日期
     */
    private LocalDate beginOrderDate;

    /**
     * 结束订单日期
     */
    private LocalDate endOrderDate;

    /**
     * 开始期望交货日期
     */
    private LocalDate beginExpectedDeliveryDate;

    /**
     * 结束期望交货日期
     */
    private LocalDate endExpectedDeliveryDate;

    /**
     * 开始创建时间
     */
    private String beginCreateTime;

    /**
     * 结束创建时间
     */
    private String endCreateTime;
}
