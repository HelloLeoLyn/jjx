package com.jjx.inventory.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 保持历史预留状态值，已消费沿用转正式预留状态2。 */
@Getter
@RequiredArgsConstructor
public enum MaterialReserveStatusEnum {
    ACTIVE(0), RELEASED(1), CONSUMED(2);
    private final int value;
}
