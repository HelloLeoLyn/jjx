package com.jjx.purchase.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单数据传输对象
 */
@Data
public class PurchaseOrderDTO {

    /**
     * 采购订单ID（更新时使用）
     */
    private Long orderId;

    /**
     * 采购订单号
     */
    @NotBlank(message = "采购订单号不能为空")
    @Size(max = 50, message = "采购订单号长度不能超过50个字符")
    private String orderNo;

    /** 链路追踪ID（DEV-568） */
    private String traceId;

    /**
     * 供应商ID
     */
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    /**
     * 供应商名称
     */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 200, message = "供应商名称长度不能超过200个字符")
    private String supplierName;

    /**
     * 订单类型（normal正常/urgent紧急）
     */
    private String orderType;

    /**
     * 订单日期
     */
    @NotNull(message = "订单日期不能为空")
    private LocalDate orderDate;

    /**
     * 期望交货日期
     */
    @NotNull(message = "期望交货日期不能为空")
    private LocalDate expectedDeliveryDate;

    /**
     * 实际交货日期
     */
    private LocalDate actualDeliveryDate;

    /**
     * 订单金额（不含税）
     */
    @NotNull(message = "订单金额不能为空")
    private BigDecimal orderAmount;

    /**
     * 订单税额
     */
    @NotNull(message = "订单税额不能为空",groups = OrderGroup.Tax.class)
    private BigDecimal orderTax;

    /**
     * 订单含税总金额
     */
    @NotNull(message = "订单含税总金额不能为空",groups = OrderGroup.Tax.class)
    private BigDecimal orderTotalAmount;

    /**
     * 币种
     */
    @NotBlank(message = "币种不能为空")
    @Size(max = 10, message = "币种长度不能超过10个字符")
    private String currency;

    /**
     * 订单审批状态（1草稿/2已取消/3待审批/4已批准/5已拒绝）
     * 合并了原 order_status 和 approval_status
     */
    private Integer approvalStatus;

    /**
     * 收货状态（0待收货/1部分收货/2已收货）
     */
    private Integer receiptStatus;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人姓名
     */
    @Size(max = 50, message = "审批人姓名长度不能超过50个字符")
    private String approverName;

    /**
     * 审批时间
     */
    private LocalDateTime approvalTime;

    /**
     * 审批意见
     */
    @Size(max = 500, message = "审批意见长度不能超过500个字符")
    private String approvalComment;

    /**
     * 付款状态（0待付款/1部分付款/2已付款）
     */
    private Integer paymentStatus;

    /**
     * 已付款金额
     */
    private BigDecimal paidAmount;

    /**
     * 合同编号
     */
    @Size(max = 50, message = "合同编号长度不能超过50个字符")
    private String contractNo;

    /**
     * 交货方式
     */
    @Size(max = 50, message = "交货方式长度不能超过50个字符")
    private String deliveryMethod;

    /**
     * 交货地址
     */
    @Size(max = 500, message = "交货地址长度不能超过500个字符")
    private String deliveryAddress;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;

    /**
     * 是否紧急（0否 1是）
     */
    private Boolean urgentFlag;

    /**
     * 紧急原因
     */
    @Size(max = 200, message = "紧急原因长度不能超过200个字符")
    private String urgentReason;

    /**
     * 订单明细项列表
     */
    private List<PurchaseOrderItemDTO> items;
}
