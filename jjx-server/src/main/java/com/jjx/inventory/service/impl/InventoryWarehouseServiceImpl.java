package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.enums.StatusEnum;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.vo.WarehouseVO;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.service.InventoryWarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import com.jjx.system.annotation.Event;

/**
 * 仓库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseServiceImpl extends ServiceImpl<InventoryWarehouseMapper, InventoryWarehouse>
        implements InventoryWarehouseService {

    private final InventoryWarehouseMapper warehouseMapper;

    @Override
    public List<InventoryWarehouse> getAllEnabled() {
        return warehouseMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouse>()
                        .eq(InventoryWarehouse::getStatus, String.valueOf(StatusEnum.NORMAL.getCode()))
                        .orderByAsc(InventoryWarehouse::getSortOrder)
        );
    }

    @Override
    public List<InventoryWarehouse> getOptions() {
        return getAllEnabled();
    }

    @Override
    public List<InventoryWarehouse> getByType(String warehouseType) {
        return warehouseMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouse>()
                        .eq(InventoryWarehouse::getWarehouseType, warehouseType)
                        .eq(InventoryWarehouse::getStatus, String.valueOf(StatusEnum.NORMAL.getCode()))
                        .orderByAsc(InventoryWarehouse::getSortOrder)
        );
    }

    @Override
    public boolean existsByCode(String warehouseCode) {
        LambdaQueryWrapper<InventoryWarehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryWarehouse::getWarehouseCode, warehouseCode);
        Long count = warehouseMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    @Event(value = "inventory.warehouse.status_updated", bizId = "#warehouseId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long warehouseId, String status) {
        InventoryWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            log.error("仓库不存在: warehouseId={}", warehouseId);
            return false;
        }

        warehouse.setStatus(status);
        return warehouseMapper.updateById(warehouse) > 0;
    }

    @Override
    @Event(value = "inventory.warehouse.deleted", bizId = "#warehouseId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long warehouseId) {
        InventoryWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            log.error("仓库不存在: warehouseId={}", warehouseId);
            return false;
        }

        // TODO: 检查是否有库位或库存
        // 这里需要调用库位Mapper和库存Mapper检查

        return warehouseMapper.deleteById(warehouseId) > 0;
    }
}
