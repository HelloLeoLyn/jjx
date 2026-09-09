package com.jjx.inventory.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 统一库存物品来源类型。业务档案仍由材料域、产品域分别维护。 */
@Getter
@RequiredArgsConstructor
public enum InventoryItemTypeEnum {
    MATERIAL("MATERIAL", "材料"),
    PRODUCT("PRODUCT", "产品");

    private final String code;
    private final String label;
}
