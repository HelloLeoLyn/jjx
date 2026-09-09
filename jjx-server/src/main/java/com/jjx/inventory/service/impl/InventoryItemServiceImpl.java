package com.jjx.inventory.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryItem;
import com.jjx.inventory.enums.InventoryItemTypeEnum;
import com.jjx.inventory.mapper.InventoryItemMapper;
import com.jjx.inventory.service.InventoryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryItemServiceImpl implements InventoryItemService {
    private final InventoryItemMapper inventoryItemMapper;

    @Override
    public InventoryItem getBySource(InventoryItemTypeEnum itemType, Long sourceId) {
        if (sourceId == null) return null;
        return inventoryItemMapper.selectBySource(itemType.getCode(), sourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryItem ensure(InventoryItemTypeEnum itemType, Long sourceId, String code,
                                String name, String specification, String unit) {
        if (sourceId == null) throw new BusinessException("库存物品来源ID不能为空");
        InventoryItem existing = getBySource(itemType, sourceId);
        if (existing != null) return existing;

        InventoryItem item = new InventoryItem();
        item.setItemType(itemType.getCode());
        item.setSourceId(sourceId);
        item.setItemCode(code);
        item.setItemName(name);
        item.setSpecification(specification);
        item.setUnit(unit);
        item.setBatchManaged(1);
        item.setLocationManaged(1);
        item.setStatus(1);
        try {
            inventoryItemMapper.insert(item);
            return item;
        } catch (DuplicateKeyException ignored) {
            return inventoryItemMapper.selectBySource(itemType.getCode(), sourceId);
        }
    }
}
