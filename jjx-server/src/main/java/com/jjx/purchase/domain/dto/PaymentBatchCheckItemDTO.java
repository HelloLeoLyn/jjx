package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款导入批量校验请求项（DEV-726）
 * 对应导入模板的每一行
 */
@Data
public class PaymentBatchCheckItemDTO {

    /** 行号（1-based，用于定位错误行） */
    private Integer rowIndex;

    /** 付款单号 */
    private String paymentNo;

    /** 采购订单ID */
    private Long orderId;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 付款金额 */
    private BigDecimal paymentAmount;

    /** 付款方式 */
    private String paymentMethod;

    /** 备注 */
    private String remark;
}
