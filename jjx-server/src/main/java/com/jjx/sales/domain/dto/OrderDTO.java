package com.jjx.sales.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售订单数据传输对象
 * 用于接收前端传入的订单数据
 */
@Data
public class OrderDTO {

    /**
     * 订单ID（修改时使用）
     */
    private Long orderId;

    /**
     * 订单编号
     */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /**
     * 报价单ID
     */
    private Long quotationId;

    /**
     * 客户ID
     */
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
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
    @NotNull(message = "订单日期不能为空")
    private LocalDate orderDate;

    /**
     * 交货日期
     */
    @NotNull(message = "交货日期不能为空")
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
     * 总数量
     */
    private Integer totalQuantity;

    /**
     * 总金额
     */
    @NotNull(message = "总金额不能为空")
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
    @NotNull(message = "最终金额不能为空")
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
}
