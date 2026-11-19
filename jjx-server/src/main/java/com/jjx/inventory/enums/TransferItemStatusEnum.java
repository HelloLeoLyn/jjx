package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 沿用 inventory_transfer_item.status 的真实数据库口径。 */
@Getter
@AllArgsConstructor
public enum TransferItemStatusEnum {
    PENDING(0), COMPLETED(1);
    private final Integer value;
}
