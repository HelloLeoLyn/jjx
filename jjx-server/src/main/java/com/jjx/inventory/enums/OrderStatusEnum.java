package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单据状态枚举
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    DRAFT("draft", "草稿"),
    PENDING("pending", "待审批"),
    APPROVED("approved", "已批准"),
    REJECTED("rejected", "已驳回"),
    PROCESSING("processing", "处理中"),
    CONFIRMED("confirmed", "已确认"),
    OUT_CONFIRM("out_confirm", "已出库"),
    IN_CONFIRM("in_confirm", "已入库"),
    CLOSED("closed", "已关闭"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String label;

    public static OrderStatusEnum getByCode(String code) {
        for (OrderStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
