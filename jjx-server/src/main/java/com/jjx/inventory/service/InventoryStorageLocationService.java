package com.jjx.inventory.service;

import com.jjx.inventory.dto.imports.StorageLocationImportDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryStorageLocation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 库位服务接口
 */
public interface InventoryStorageLocationService extends IService<InventoryStorageLocation> {

    /**
     * 获取指定仓库下的库位列表
     */
    List<InventoryStorageLocation> getByWarehouseId(Long warehouseId);

    /**
     * 获取库位下拉选项
     */
    List<Map<String, Object>> getOptions(Long warehouseId);

    /**
     * 推荐入库库位
     */
    InventoryStorageLocation recommendLocation(Long warehouseId, Long materialId, BigDecimal quantity);

    /**
     * 更新库位已使用容量
     */
    void updateUsedCapacity(Long locationId, BigDecimal quantity);

    /**
     * 检查库位容量是否充足
     */
    boolean hasCapacity(Long locationId, BigDecimal quantity);

    /**
     * 检查库位编码是否存在
     */
    boolean existsByCode(String locationCode);

    /**
     * 启用/停用库位
     */
    boolean updateStatus(Long locationId, String status);

    /**
     * 删除库位（检查是否有库存）
     */
    boolean deleteWithCheck(Long locationId);

    /**
     * 导入库位数据
     */
    String importStorageLocation(List<StorageLocationImportDTO> importList, Long warehouseId, String operName);
}
