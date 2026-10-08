package com.jjx.sales.domain.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 待发货凭证对成品的占用，由单据逐行聚合，不另存余额。 */
@Data
public class DeliveryPendingQuantityVO {
    private Long orderId;
    private BigDecimal quantity;
}
