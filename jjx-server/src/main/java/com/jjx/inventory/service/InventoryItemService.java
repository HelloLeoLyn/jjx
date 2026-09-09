package com.jjx.inventory.service;

import com.jjx.inventory.domain.InventoryItem;
import com.jjx.inventory.enums.InventoryItemTypeEnum;

public interface InventoryItemService {
    InventoryItem getBySource(InventoryItemTypeEnum itemType, Long sourceId);

    InventoryItem ensure(InventoryItemTypeEnum itemType, Long sourceId, String code,
                         String name, String specification, String unit);
}
