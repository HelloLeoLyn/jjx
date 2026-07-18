package com.jjx.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购付款表实体类
 * 对应表：purchase_payment
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_payment")
public class PurchasePayment {

    /**
     * 付款ID
     */
    @TableId(value = "payment_id", type = IdType.AUTO)
    private Long paymentId;

    /**
     * 付款单号
     */
    private String paymentNo;

    /**
     * 采购订单ID
     */
    private Long orderId;

    /**
     * 票据ID
     */
    private Long documentId;

    /**
     * 付款日期
     */
    private LocalDate paymentDate;

    /**
     * 付款金额
     */
    private BigDecimal paymentAmount;

    /**
     * 付款方式（bank银行转账/cash现金/check支票）
     */
    private String paymentMethod;

    /**
     * 银行账户
     */
    private String bankAccount;

    /**
     * 付款状态（pending待付款/approved已批准/paid已付款）
     */
    private String paymentStatus;

    /**
     * 批准时间
     */
    private LocalDateTime approvalTime;

    /**
     * 实际付款日期
     */
    private LocalDate actualPaymentDate;

    /**
     * 凭证编号
     */
    private String voucherNo;

    /**
     * 凭证文件URL
     */
    private String voucherFileUrl;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;
}
