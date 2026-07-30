package com.jjx.purchase.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单视图对象
 */
@Data
public class PurchaseOrderVO {

    /**
     * 采购订单ID
     */
    private Long orderId;

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
     */

    /**
     * 订单日期
     */
    private LocalDate orderDate;

    /**
     * 期望交货日期
     */
    private LocalDate expectedDeliveryDate;

    /**
     * 实际交货日期
     */
    private LocalDate actualDeliveryDate;

    /**
     * 订单金额（不含税）
     */
    private BigDecimal orderAmount;

    /**
     * 订单税额
     */
    private BigDecimal orderTax;

    /**
     * 订单含税总金额
     */
    private BigDecimal orderTotalAmount;

    /**
     * 币种
     */
    private String currency;

    /**
     * 订单审批状态（1草稿/2已取消/3待审批/4已批准/5已拒绝）
     * 合并了原 order_status 和 approval_status
     */
    private Integer approvalStatus;

    /**
     */

    /**
     * 收货状态（0待收货/1部分收货/2已收货）
     */
    private Integer receiptStatus;

    /**
     */

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人姓名
     */
    private String approverName;

    /**
     * 审批时间
     */
    private LocalDateTime approvalTime;

    /**
     * 审批意见
     */
    private String approvalComment;

    /**
     * 付款状态（0待付款/1部分付款/2已付款）
     */
    private Integer paymentStatus;

    /**
     */

    /**
     * 已付款金额
     */
    private BigDecimal paidAmount;

    /**
     * 合同编号
     */
    private String contractNo;

    /**
     * 交货方式
     */
    private String deliveryMethod;

    /**
     * 交货地址
     */
    private String deliveryAddress;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否紧急（0否 1是）
     */
    private Boolean urgentFlag;

    /**
     * 紧急原因
     */
    private String urgentReason;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 订单明细项列表
     */
    private List<PurchaseOrderItemVO> items;
}
