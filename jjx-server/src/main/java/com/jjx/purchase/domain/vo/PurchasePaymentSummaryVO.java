package com.jjx.purchase.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 同一订单的付款申请额度；编辑时申请中金额排除本单。 */
@Data
public class PurchasePaymentSummaryVO {
    private Long orderId;
    private String orderNo;
    private String supplierName;
    private BigDecimal orderTotalAmount;
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
    private BigDecimal availableAmount;
    private String currency;
}
