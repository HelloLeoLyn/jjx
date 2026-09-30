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
        } catch (DuplicateKeyException e) {
            // ① 撞的是 uk_inventory_item_source（同来源并发/幂等）：按来源回查即可
            InventoryItem bySource = inventoryItemMapper.selectBySource(itemType.getCode(), sourceId);
            if (bySource != null) {
                return bySource;
            }
            // ② 撞的是 uk_inventory_item_code：该编码被「别的来源」占用。
            //    典型场景（dev-20260930-010）：产品被物理删除（草稿回收/测试数据清理）后，
            //    其 PRODUCT 库存身份行残留，编码被新档案复用 → 此处按 source 回查必然为空。
            //    原实现直接 return selectBySource(...) → 返回 null → 调用方 NPE，
            //    且异常被上游 try/catch 吞掉后把事务标为 rollback-only → 整单回滚。
            //    改为明确报错，暴露编码/数据冲突。
            InventoryItem byCode = code == null ? null : inventoryItemMapper.selectByCode(code);
            if (byCode != null) {
                throw new BusinessException("库存物品编码[" + code + "]已被占用（inventory_item_id="
                        + byCode.getInventoryItemId() + "，类型=" + byCode.getItemType()
                        + "，来源ID=" + byCode.getSourceId() + "），无法为 "
                        + itemType.getCode() + ":" + sourceId + " 创建库存身份，请先清理冲突数据");
            }
            throw new BusinessException("库存物品唯一键冲突（" + itemType.getCode() + ":" + sourceId + "）："
                    + e.getMostSpecificCause().getMessage());
        }
    }
}
