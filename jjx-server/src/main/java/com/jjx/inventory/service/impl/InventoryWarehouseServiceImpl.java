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
    /** 2026-09-21（dev-20260921-013）：库存事件改手写 payload。 */
    private final com.jjx.event.EventPublisher eventPublisher;

    /**
     * 库存主数据/预警事件统一发布（2026-09-21 dev-20260921-013 库存批 3/3）：
     * 手写 payload，bizNo 取对象编码（删除类由调用方传入删除前取到的编码）。
     */
    private void publishWarehouseEvent(String eventCode, Long id, String knownCode) {
        InventoryWarehouse m = (id == null || knownCode != null) ? null : warehouseMapper.selectById(id);
        String code = knownCode != null ? knownCode : (m == null ? null : m.getWarehouseCode());
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "warehouse", id, code);
        // 删除路径由调用方传 knownCode，此时 m 为 null —— 不防空会 NPE（2026-09-21 dev-20260921-023）
        if (m != null) {
            payload.put("warehouseName", m.getWarehouseName());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

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
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long warehouseId, String status) {
        InventoryWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            log.error("仓库不存在: warehouseId={}", warehouseId);
            return false;
        }

        warehouse.setStatus(status);
        publishWarehouseEvent("inventory.warehouse.status_updated", warehouseId, null);
        return warehouseMapper.updateById(warehouse) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long warehouseId) {
        InventoryWarehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            log.error("仓库不存在: warehouseId={}", warehouseId);
            return false;
        }

        // TODO: 检查是否有库位或库存
        // 这里需要调用库位Mapper和库存Mapper检查

        boolean updated = warehouseMapper.deleteById(warehouseId) > 0;
        if (updated) {
            publishWarehouseEvent("inventory.warehouse.deleted", warehouse.getWarehouseId(), warehouse.getWarehouseCode());
        }
        return updated;
    }
}
