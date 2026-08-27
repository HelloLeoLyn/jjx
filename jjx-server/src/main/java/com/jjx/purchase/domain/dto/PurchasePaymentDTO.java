package com.jjx.purchase.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购付款数据传输对象
 */
@Data
public class PurchasePaymentDTO {

    /**
     * 付款ID（更新时使用）
     */
    private Long paymentId;

    /**
     * 付款单号
     */
    @NotBlank(message = "付款单号不能为空")
    @Size(max = 50, message = "付款单号长度不能超过50个字符")
    private String paymentNo;

    /**
     * 采购订单ID
     */
    @NotNull(message = "采购订单ID不能为空")
    private Long orderId;

    /**
     * 票据ID
     */
    private Long documentId;

    /**
     * 付款日期
     */
    @NotNull(message = "付款日期不能为空")
    private LocalDate paymentDate;

    /**
     * 付款金额
     */
    @NotNull(message = "付款金额不能为空")
    private BigDecimal paymentAmount;

    /**
     * 付款方式（bank银行转账/cash现金/check支票）
     */
    @NotBlank(message = "付款方式不能为空")
    @Pattern(regexp = "^(bank|cash|check)$", message = "付款方式必须是bank、cash或check")
    private String paymentMethod;

    /**
     * 银行账户
     */
    @Size(max = 100, message = "银行账户长度不能超过100个字符")
    private String bankAccount;

    /**
     * 付款状态（pending待付款/approved已批准/paid已付款）
     */
    @NotNull(message = "付款状态不能为空")
    private Integer paymentStatus;

    /**
     * 批准时间
     */
    private LocalDate approvalTime;

    /**
     * 实际付款日期
     */
    private LocalDate actualPaymentDate;

    /**
     * 凭证编号
     */
    @Size(max = 50, message = "凭证编号长度不能超过50个字符")
    private String voucherNo;

    /**
     * 凭证文件URL
     */
    @Size(max = 500, message = "凭证文件URL长度不能超过500个字符")
    private String voucherFileUrl;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
