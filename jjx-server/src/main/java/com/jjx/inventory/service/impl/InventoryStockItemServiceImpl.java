package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.converter.StockItemConverter;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.dto.query.StockItemQueryDTO;
import com.jjx.inventory.dto.vo.StockItemVO;
import com.jjx.inventory.enums.StockItemStatusEnum;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStorageLocationMapper;
import com.jjx.inventory.service.InventoryStockItemService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存批次明细 Service 实现
 */
@Service
@RequiredArgsConstructor
public class InventoryStockItemServiceImpl extends ServiceImpl<InventoryStockItemMapper, InventoryStockItem> implements InventoryStockItemService {

    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStorageLocationMapper storageLocationMapper;
    private final StockItemConverter stockItemConverter;

    @Override
    public IPage<StockItemVO> page(StockItemQueryDTO query) {
        LambdaQueryWrapper<InventoryStockItem> wrapper = new LambdaQueryWrapper<>();

        if (query.getMaterialId() != null) {
            wrapper.eq(InventoryStockItem::getMaterialId, query.getMaterialId());
        }
        if (StringUtils.isNotBlank(query.getMaterialCode())) {
            wrapper.like(InventoryStockItem::getMaterialCode, query.getMaterialCode());
        }
        if (StringUtils.isNotBlank(query.getMaterialName())) {
            wrapper.like(InventoryStockItem::getMaterialName, query.getMaterialName());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryStockItem::getWarehouseId, query.getWarehouseId());
        }
        if (query.getLocationId() != null) {
            wrapper.eq(InventoryStockItem::getLocationId, query.getLocationId());
        }
        if (StringUtils.isNotBlank(query.getBatchNo())) {
            wrapper.like(InventoryStockItem::getBatchNo, query.getBatchNo());
        }
        if (query.getStatus() != null) {
            wrapper.eq(InventoryStockItem::getStatus, query.getStatus());
        }

        wrapper.orderByAsc(InventoryStockItem::getExpiryDate)
                .orderByAsc(InventoryStockItem::getLastInboundTime);

        Page<InventoryStockItem> pageParam = new Page<>(query.getCurrent(), query.getPageSize());
        Page<InventoryStockItem> pageResult = stockItemMapper.selectPage(pageParam, wrapper);

        // 转换为VO
        List<StockItemVO> voList = pageResult.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<StockItemVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public StockItemVO getById(Long itemId) {
        InventoryStockItem entity = stockItemMapper.selectById(itemId);
        return entity != null ? convertToVO(entity) : null;
    }

    @Override
    public List<StockItemVO> getByMaterialId(Long materialId) {
        List<InventoryStockItem> list = stockItemMapper.selectActiveByMaterialId(materialId);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<StockItemVO> getByMaterialAndWarehouse(Long materialId, Long warehouseId) {
        List<InventoryStockItem> list = stockItemMapper.selectActiveByMaterialAndWarehouse(materialId, warehouseId);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 将实体转换为VO，并填充关联信息
     */
    private StockItemVO convertToVO(InventoryStockItem entity) {
        StockItemVO vo = stockItemConverter.toVO(entity);

        // 设置状态名称
        StockItemStatusEnum statusEnum = StockItemStatusEnum.getByCode(entity.getStatus());
        if (statusEnum != null) {
            vo.setStatusName(statusEnum.getLabel());
        }

        // 填充库位名称（DEV-692）
        if (entity.getLocationId() != null) {
            InventoryStorageLocation loc = storageLocationMapper.selectById(entity.getLocationId());
            if (loc != null) {
                vo.setLocationCode(loc.getLocationCode());
                vo.setLocationName(loc.getLocationName());
            }
        }

        return vo;
    }
}
