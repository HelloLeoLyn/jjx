package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 与既有LocationStatusEnum、库位表及迁移口径一致，禁止套用通用StatusEnum。 */
@Getter
@AllArgsConstructor
public enum StorageLocationStatusEnum {
    NORMAL("0"), DISABLED("1");
    private final String code;
}
