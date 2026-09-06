package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IqcQuarantineStatusEnum {
    PENDING("PENDING", "待处置"), RELEASED("RELEASED", "已释放"),
    RETURNED("RETURNED", "已退货"), REWORKED("REWORKED", "已返工"),
    SCRAPPED("SCRAPPED", "已报废");
    private final String code;
    private final String label;
}
