package com.jjx.inventory.stocktake;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
/** 沿用盘点明细已有数值口径，不修改原枚举对应值。 */
@Getter
@RequiredArgsConstructor
public enum QuickAdjustStatus {
    PENDING(0),PROCESSED(1);
    private final Integer value;
}
