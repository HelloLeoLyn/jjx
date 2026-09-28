package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IqcDispositionOrderStatusEnum {
    COMPLETED("COMPLETED", "已完成"),
    PENDING_INBOUND("PENDING_INBOUND", "待入库"),
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批"),
    CANCELLED("CANCELLED", "已取消");
    private final String code;
    private final String label;
}
