package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.enums.StorageLocationStatusEnum;
import com.jjx.inventory.location.LocationHierarchyService;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.dto.imports.StorageLocationImportDTO;
import com.jjx.inventory.mapper.InventoryStorageLocationMapper;
import com.jjx.inventory.service.InventoryStorageLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库位服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStorageLocationServiceImpl extends ServiceImpl<InventoryStorageLocationMapper, InventoryStorageLocation>
        implements InventoryStorageLocationService {

    private final InventoryStorageLocationMapper storageLocationMapper;
    private final LocationHierarchyService hierarchy;
    /** 2026-09-21（dev-20260921-013）：库存事件改手写 payload。 */
    private final com.jjx.event.EventPublisher eventPublisher;

    /**
     * 库存主数据/预警事件统一发布（2026-09-21 dev-20260921-013 库存批 3/3）：
     * 手写 payload，bizNo 取对象编码（删除类由调用方传入删除前取到的编码）。
     */
    private void publishLocationEvent(String eventCode, Long id, String knownCode) {
        InventoryStorageLocation m = (id == null || knownCode != null) ? null : storageLocationMapper.selectById(id);
        String code = knownCode != null ? knownCode : (m == null ? null : m.getLocationCode());
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "storage_location", id, code);
        // 删除路径由调用方传 knownCode，此时 m 为 null —— 不防空会 NPE（2026-09-21 dev-20260921-023）
        if (m != null) {
            payload.put("locationName", m.getLocationName());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

    @Override
    public List<InventoryStorageLocation> getByWarehouseId(Long warehouseId) {
        if(warehouseId==null)return List.of();
        List<InventoryStorageLocation> result=new ArrayList<>();
        collectLeaves(hierarchy.tree(warehouseId),result,true);
        return result;
    }

    private static void collectLeaves(List<LocationHierarchyService.Node> nodes,List<InventoryStorageLocation> result,boolean ancestorsEnabled) {
        for(var node:nodes){
            boolean enabled=ancestorsEnabled && StorageLocationStatusEnum.NORMAL.getCode().equals(node.getStatus());
            if(node.leaf && enabled){node.setLocationName(node.path);result.add(node);}
            collectLeaves(node.children,result,enabled);
        }
    }

    @Override
    public List<Map<String, Object>> getOptions(Long warehouseId) {
        List<InventoryStorageLocation> locations=new ArrayList<>();
        if(warehouseId!=null)locations.addAll(getByWarehouseId(warehouseId));
        else for(Long id:storageLocationMapper.selectList(new LambdaQueryWrapper<InventoryStorageLocation>()).stream().map(InventoryStorageLocation::getWarehouseId).distinct().toList()) locations.addAll(getByWarehouseId(id));
        return locations.stream().map(location->{
            Map<String,Object> option=new HashMap<>();
            option.put("value",location.getLocationId());option.put("label",location.getLocationName()+" ("+location.getLocationCode()+")");
            option.put("code",location.getLocationCode());option.put("name",location.getLocationName());option.put("warehouseId",location.getWarehouseId());
            option.put("capacityEnabled",location.getCapacityEnabled());option.put("capacityUnit",location.getCapacityUnit());
            option.put("capacity",location.getCapacity());option.put("usedCapacity",location.getUsedCapacity());
            option.put("availableCapacity",Boolean.TRUE.equals(location.getCapacityEnabled()) && location.getCapacity()!=null && location.getUsedCapacity()!=null?location.getCapacity().subtract(location.getUsedCapacity()):null);
            return option;
        }).toList();
    }

    @Override
    public InventoryStorageLocation recommendLocation(Long warehouseId, Long materialId, BigDecimal quantity) {
        // 旧接口没有记账单位，不能证明受限容量足够；仅推荐未启用容量管理的位置。
        return getByWarehouseId(warehouseId).stream().filter(node->!Boolean.TRUE.equals(node.getCapacityEnabled()))
                .filter(node->hasCapacity(node.getLocationId(),quantity)).findFirst().orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUsedCapacity(Long locationId, BigDecimal quantity) {
        throw new BusinessException("已用容量由库存流水派生，不支持直接修改");
    }

    @Override
    public boolean hasCapacity(Long locationId, BigDecimal quantity) {
        if(locationId==null || quantity==null || quantity.signum()<0)return false;
        var location=storageLocationMapper.selectById(locationId);if(location==null)return false;
        // 无单位参数的旧接口保守判断；实际入账仍由唯一库存变动入口精确核验祖先容量。
        var index=new java.util.HashMap<Long,InventoryStorageLocation>();
        storageLocationMapper.selectList(new LambdaQueryWrapper<InventoryStorageLocation>().eq(InventoryStorageLocation::getWarehouseId,location.getWarehouseId())).forEach(row->index.put(row.getLocationId(),row));
        var visited=new java.util.HashSet<Long>();
        for(var node=location;node!=null;node=node.getParentId()==null?null:index.get(node.getParentId())){
            if(!visited.add(node.getLocationId()) || !StorageLocationStatusEnum.NORMAL.getCode().equals(node.getStatus()) || Boolean.TRUE.equals(node.getCapacityEnabled()))return false;
        }
        return getByWarehouseId(location.getWarehouseId()).stream().anyMatch(node->node.getLocationId().equals(locationId));
    }

    @Override
    public boolean existsByCode(String locationCode) {
        if (locationCode == null || locationCode.isEmpty()) {
            return false;
        }

        LambdaQueryWrapper<InventoryStorageLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryStorageLocation::getLocationCode, locationCode);

        Long count = storageLocationMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long locationId, String status) {
        hierarchy.status(locationId,status,com.jjx.system.utils.SecurityUtils.getUsername());
        publishLocationEvent("inventory.storage_location.status_updated",locationId,null);return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long locationId) {
        var location=storageLocationMapper.selectById(locationId);
        hierarchy.delete(locationId);
        publishLocationEvent("inventory.storage_location.deleted",locationId,location.getLocationCode());return true;
    }

    private static BigDecimal number(String value){return value==null || value.isBlank()?null:new BigDecimal(value);}

    /**
     * 批量更新库位状态
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateStatus(List<Long> locationIds, String status) {
        if(locationIds==null || locationIds.isEmpty())throw new BusinessException("请选择库位");
        for(Long id:locationIds)updateStatus(id,status);return true;
    }

    /**
     * 获取库位的可用容量
     */
    public BigDecimal getAvailableCapacity(Long locationId) {
        var location=storageLocationMapper.selectById(locationId);if(location==null)return null;
        for(var node:getByWarehouseId(location.getWarehouseId()))if(node.getLocationId().equals(locationId))
            return Boolean.TRUE.equals(node.getCapacityEnabled()) && node.getCapacity()!=null && node.getUsedCapacity()!=null?node.getCapacity().subtract(node.getUsedCapacity()):null;
        return null;
    }

    /**
     * 重置库位已使用容量（用于盘点后调整）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resetUsedCapacity(Long locationId, BigDecimal newUsedCapacity) {
        throw new BusinessException("已用容量由库存流水派生，不支持直接重置");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importStorageLocation(List<StorageLocationImportDTO> importList, Long warehouseId, String operName) {
        if(importList==null || importList.isEmpty())throw new BusinessException("导入数据为空");
        if(importList.size()>1000)throw new BusinessException("每次最多导入1000条");
        for(int i=0;i<importList.size();i++){
            var dto=importList.get(i);var location=new InventoryStorageLocation();
            try{
                location.setWarehouseId(warehouseId);location.setLocationCode(dto.getLocationCode());location.setLocationName(dto.getLocationName());
                location.setLocationType(dto.getLocationType());location.setStatus(StorageLocationStatusEnum.NORMAL.getCode());location.setCapacityEnabled(false);
                location.setRemark(dto.getRemark());
                location.setCapacity(number(dto.getCapacity()));location.setWidth(number(dto.getWidth()));location.setHeight(number(dto.getHeight()));location.setDepth(number(dto.getDepth()));
                if(dto.getSortOrder()!=null && !dto.getSortOrder().isBlank())location.setSortOrder(Integer.valueOf(dto.getSortOrder()));
                hierarchy.saveNode(location,operName);
            }catch(Exception e){throw new BusinessException("第"+(i+2)+"行导入失败，整批未保存："+e.getMessage());}
        }
        return "导入完成，成功 "+importList.size()+" 条；名称按原文保留，未启用容量管理";
    }
}
