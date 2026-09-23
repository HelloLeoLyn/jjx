package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 销售发货状态（sales_delivery.delivery_status）。 */
@Getter
@AllArgsConstructor
public enum SalesDeliveryStatusEnum implements BizStatusEnum {
    PENDING(1, "待发货"),
    SHIPPED(2, "已发货"),
    RECEIVED(4, "已签收"),
    REJECTED(5, "已拒收");

    private final Integer value;
    private final String label;
}
