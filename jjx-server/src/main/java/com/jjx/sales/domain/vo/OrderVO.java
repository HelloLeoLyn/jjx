package com.jjx.sales.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单视图对象
 * 用于返回给前端的订单数据
 */
@Data
public class OrderVO {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 报价单ID
     */
    private Long quotationId;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 订单日期
     */
    private LocalDate orderDate;

    /**
     * 交货日期
     */
    private LocalDate deliveryDate;

    /**
     * 币种
     */
    private String currency;

    /**
     * 汇率
     */
    private BigDecimal exchangeRate;

    /**
     * 付款条件
     */
    private String paymentTerms;

    /**
     * 交货条件
     */
    private String deliveryTerms;

    /**
     * 交货地址
     */
    private String deliveryAddress;

    /**
     * 订单状态
     */
    private Integer orderStatus;

    /**
     * 订单状态文本
     */
    private String orderStatusText;

    /**
     * 总数量
     */
    private Integer totalQuantity;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 税率
     */
    private BigDecimal taxRate;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 含税总金额
     */
    private BigDecimal totalAmountWithTax;

    /**
     * 折扣率
     */
    private BigDecimal discountRate;

    /**
     * 折扣金额
     */
    private BigDecimal discountAmount;

    /**
     * 最终金额
     */
    private BigDecimal finalAmount;

    /**
     * 已付金额
     */
    private BigDecimal paidAmount;

    /**
     * 未付金额
     */
    private BigDecimal unpaidAmount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 销售负责人ID
     */
    private Long salesManagerId;

    /**
     * 销售负责人姓名
     */
    private String salesManagerName;

    /**
     * 审核人ID
     */
    private Long approverId;

    /**
     * 审核人姓名
     */
    private String approverName;

    /**
     * 审核时间
     */
    private LocalDateTime approveTime;

    /**
     * 审核备注
     */
    private String approveRemark;

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
     * 格式化后的总金额
     */
    private String formattedTotalAmount;

    /**
     * 格式化后的最终金额
     */
    private String formattedFinalAmount;

    /**
     * 格式化后的已付金额
     */
    private String formattedPaidAmount;

    /**
     * 格式化后的未付金额
     */
    private String formattedUnpaidAmount;

    /**
     * 订单明细数量
     */
    private Integer itemCount;

    /**
     * 是否可编辑
     */
    private Boolean editable;

    /**
     * 是否可删除
     */
    private Boolean deletable;

    /**
     * 是否可审核
     */
    private Boolean reviewable;

    /**
     * 是否可确认
     */
    private Boolean confirmable;

    /**
     * 是否可创建实例
     */
    private Boolean instanceCreatable;
}
