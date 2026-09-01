package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 销售退货单状态枚举（sales_return.return_status）
 */
@Getter
@AllArgsConstructor
public enum SalesReturnStatusEnum implements BizStatusEnum {
    APPLYING(1, "申请中"),
    APPROVED(2, "已审核"),
    RECEIVED(3, "已收货"),
    REFUNDED(4, "已退款"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消");

    private final Integer value;
    private final String label;

    public static SalesReturnStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SalesReturnStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
