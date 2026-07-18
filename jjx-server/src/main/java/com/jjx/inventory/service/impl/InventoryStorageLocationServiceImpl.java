package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.stream.Collectors;

/**
 * 库位服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStorageLocationServiceImpl extends ServiceImpl<InventoryStorageLocationMapper, InventoryStorageLocation>
        implements InventoryStorageLocationService {

    private final InventoryStorageLocationMapper storageLocationMapper;

    @Override
    public List<InventoryStorageLocation> getByWarehouseId(Long warehouseId) {
        if (warehouseId == null) {
            log.error("仓库ID不能为空");
            return List.of();
        }

        LambdaQueryWrapper<InventoryStorageLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryStorageLocation::getWarehouseId, warehouseId);
        wrapper.eq(InventoryStorageLocation::getStatus, "0"); // 只查询启用的库位
        wrapper.orderByAsc(InventoryStorageLocation::getSortOrder);

        return storageLocationMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> getOptions(Long warehouseId) {
        LambdaQueryWrapper<InventoryStorageLocation> wrapper = new LambdaQueryWrapper<>();

        if (warehouseId != null) {
            wrapper.eq(InventoryStorageLocation::getWarehouseId, warehouseId);
        }
        wrapper.eq(InventoryStorageLocation::getStatus, "0"); // 只查询启用的库位
        wrapper.orderByAsc(InventoryStorageLocation::getSortOrder);

        List<InventoryStorageLocation> locations = storageLocationMapper.selectList(wrapper);

        return locations.stream()
                .map(location -> {
                    Map<String, Object> option = new HashMap<>();
                    option.put("value", location.getLocationId());
                    option.put("label", location.getLocationName() + " (" + location.getLocationCode() + ")");
                    option.put("code", location.getLocationCode());
                    option.put("name", location.getLocationName());
                    option.put("warehouseId", location.getWarehouseId());
                    option.put("capacity", location.getCapacity());
                    option.put("usedCapacity", location.getUsedCapacity());
                    option.put("availableCapacity", location.getCapacity().subtract(location.getUsedCapacity()));
                    return option;
                })
                .collect(Collectors.toList());
    }

    @Override
    public InventoryStorageLocation recommendLocation(Long warehouseId, Long materialId, BigDecimal quantity) {
        if (warehouseId == null || quantity == null) {
            log.error("仓库ID和数量不能为空");
            return null;
        }

        // 获取指定仓库下所有启用的库位
        List<InventoryStorageLocation> locations = getByWarehouseId(warehouseId);

        if (locations.isEmpty()) {
            log.warn("仓库 {} 下没有可用的库位", warehouseId);
            return null;
        }

        // 推荐策略：优先选择容量充足且已使用容量最少的库位
        return locations.stream()
                .filter(location -> hasCapacity(location.getLocationId(), quantity))
                .sorted((l1, l2) -> {
                    // 按已使用容量升序排序（优先使用空余容量多的库位）
                    BigDecimal usedRatio1 = l1.getUsedCapacity().divide(l1.getCapacity(), 4, BigDecimal.ROUND_HALF_UP);
                    BigDecimal usedRatio2 = l2.getUsedCapacity().divide(l2.getCapacity(), 4, BigDecimal.ROUND_HALF_UP);
                    return usedRatio1.compareTo(usedRatio2);
                })
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUsedCapacity(Long locationId, BigDecimal quantity) {
        if (locationId == null || quantity == null) {
            log.error("库位ID和数量不能为空");
            return;
        }

        InventoryStorageLocation location = storageLocationMapper.selectById(locationId);
        if (location == null) {
            log.error("库位不存在: {}", locationId);
            return;
        }

        // 计算新的已使用容量
        BigDecimal newUsedCapacity = location.getUsedCapacity().add(quantity);

        // 检查容量是否足够
        if (newUsedCapacity.compareTo(location.getCapacity()) > 0) {
            log.error("库位容量不足，当前容量: {}, 已使用: {}, 需要: {}",
                    location.getCapacity(), location.getUsedCapacity(), quantity);
            return;
        }

        // 更新已使用容量
        location.setUsedCapacity(newUsedCapacity);
        storageLocationMapper.updateById(location);

        log.info("更新库位 {} 已使用容量: {} -> {}", locationId, location.getUsedCapacity().subtract(quantity), newUsedCapacity);
    }

    @Override
    public boolean hasCapacity(Long locationId, BigDecimal quantity) {
        if (locationId == null || quantity == null) {
            return false;
        }

        InventoryStorageLocation location = storageLocationMapper.selectById(locationId);
        if (location == null || !"0".equals(location.getStatus())) {
            return false;
        }

        // 计算可用容量
        BigDecimal availableCapacity = location.getCapacity().subtract(location.getUsedCapacity());
        return availableCapacity.compareTo(quantity) >= 0;
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
        if (locationId == null || status == null) {
            log.error("库位ID和状态不能为空");
            return false;
        }

        // 验证状态值
        if (!"0".equals(status) && !"1".equals(status)) {
            log.error("状态值无效，只能为0（启用）或1（停用）");
            return false;
        }

        InventoryStorageLocation location = storageLocationMapper.selectById(locationId);
        if (location == null) {
            log.error("库位不存在: {}", locationId);
            return false;
        }

        // 如果要停用库位，检查是否有库存
        if ("1".equals(status) && location.getUsedCapacity().compareTo(BigDecimal.ZERO) > 0) {
            log.error("库位有库存，无法停用");
            return false;
        }

        location.setStatus(status);
        return storageLocationMapper.updateById(location) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long locationId) {
        if (locationId == null) {
            log.error("库位ID不能为空");
            return false;
        }

        InventoryStorageLocation location = storageLocationMapper.selectById(locationId);
        if (location == null) {
            log.error("库位不存在: {}", locationId);
            return false;
        }

        // 检查是否有库存
        if (location.getUsedCapacity().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("库位 {} 有库存，无法删除", locationId);
            return false;
        }

        return storageLocationMapper.deleteById(locationId) > 0;
    }

    /**
     * 批量更新库位状态
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateStatus(List<Long> locationIds, String status) {
        if (locationIds == null || locationIds.isEmpty() || status == null) {
            log.error("库位ID列表和状态不能为空");
            return false;
        }

        // 验证状态值
        if (!"0".equals(status) && !"1".equals(status)) {
            log.error("状态值无效，只能为0（启用）或1（停用）");
            return false;
        }

        boolean allSuccess = true;
        for (Long locationId : locationIds) {
            try {
                if (!updateStatus(locationId, status)) {
                    allSuccess = false;
                    log.error("更新库位 {} 状态失败", locationId);
                }
            } catch (Exception e) {
                allSuccess = false;
                log.error("更新库位 {} 状态时发生异常: {}", locationId, e.getMessage());
            }
        }

        return allSuccess;
    }

    /**
     * 获取库位的可用容量
     */
    public BigDecimal getAvailableCapacity(Long locationId) {
        if (locationId == null) {
            return BigDecimal.ZERO;
        }

        InventoryStorageLocation location = storageLocationMapper.selectById(locationId);
        if (location == null || !"0".equals(location.getStatus())) {
            return BigDecimal.ZERO;
        }

        return location.getCapacity().subtract(location.getUsedCapacity());
    }

    /**
     * 重置库位已使用容量（用于盘点后调整）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resetUsedCapacity(Long locationId, BigDecimal newUsedCapacity) {
        if (locationId == null || newUsedCapacity == null) {
            log.error("库位ID和新的已使用容量不能为空");
            return false;
        }

        InventoryStorageLocation location = storageLocationMapper.selectById(locationId);
        if (location == null) {
            log.error("库位不存在: {}", locationId);
            return false;
        }

        // 检查新的已使用容量是否超过容量
        if (newUsedCapacity.compareTo(location.getCapacity()) > 0) {
            log.error("新的已使用容量超过库位容量");
            return false;
        }

        location.setUsedCapacity(newUsedCapacity);
        return storageLocationMapper.updateById(location) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importStorageLocation(List<StorageLocationImportDTO> importList, Long warehouseId, String operName) {
        if (importList == null || importList.isEmpty()) {
            return "导入数据为空";
        }

        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < importList.size(); i++) {
            StorageLocationImportDTO dto = importList.get(i);
            int rowNum = i + 2; // 第1行是表头，从第2行开始

            try {
                // 检查库位编码是否已存在
                if (existsByCode(dto.getLocationCode())) {
                    errors.add(String.format("第%d行: 库位编码 '%s' 已存在", rowNum, dto.getLocationCode()));
                    failCount++;
                    continue;
                }

                InventoryStorageLocation location = new InventoryStorageLocation();
                location.setWarehouseId(warehouseId);
                location.setLocationCode(dto.getLocationCode());
                location.setLocationName(dto.getLocationName());
                location.setLocationType(dto.getLocationType());
                location.setStatus("0"); // 默认启用

                // 转换数值字段
                if (dto.getCapacity() != null && !dto.getCapacity().isEmpty()) {
                    location.setCapacity(new BigDecimal(dto.getCapacity()));
                } else {
                    location.setCapacity(new BigDecimal("99999")); // 默认大容量
                }
                location.setUsedCapacity(new BigDecimal("0"));

                if (dto.getWidth() != null && !dto.getWidth().isEmpty()) {
                    location.setWidth(new BigDecimal(dto.getWidth()));
                }
                if (dto.getHeight() != null && !dto.getHeight().isEmpty()) {
                    location.setHeight(new BigDecimal(dto.getHeight()));
                }
                if (dto.getDepth() != null && !dto.getDepth().isEmpty()) {
                    location.setDepth(new BigDecimal(dto.getDepth()));
                }
                if (dto.getSortOrder() != null && !dto.getSortOrder().isEmpty()) {
                    location.setSortOrder(Integer.parseInt(dto.getSortOrder()));
                }

                storageLocationMapper.insert(location);
                successCount++;
                log.info("导入库位成功: {}", dto.getLocationCode());

            } catch (Exception e) {
                errors.add(String.format("第%d行: %s", rowNum, e.getMessage()));
                failCount++;
                log.error("导入库位失败: {}", dto.getLocationCode(), e);
            }
        }

        StringBuilder message = new StringBuilder();
        message.append("导入完成，成功 ").append(successCount).append(" 条");
        if (failCount > 0) {
            message.append("，失败 ").append(failCount).append(" 条");
        }
        if (!errors.isEmpty()) {
            message.append(":\n").append(String.join("\n", errors));
        }

        return message.toString();
    }
}
