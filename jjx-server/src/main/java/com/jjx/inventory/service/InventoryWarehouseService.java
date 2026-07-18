package com.jjx.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryWarehouse;

import java.util.List;

/**
 * 仓库服务接口
 */
public interface InventoryWarehouseService extends IService<InventoryWarehouse> {

    /**
     * 获取所有启用的仓库
     */
    List<InventoryWarehouse> getAllEnabled();

    /**
     * 获取仓库下拉选项
     */
    List<InventoryWarehouse> getOptions();

    /**
     * 根据仓库类型获取仓库
     */
    List<InventoryWarehouse> getByType(String warehouseType);

    /**
     * 检查仓库编码是否存在
     */
    boolean existsByCode(String warehouseCode);

    /**
     * 启用/停用仓库
     */
    boolean updateStatus(Long warehouseId, String status);

    /**
     * 删除仓库（检查是否有库位或库存）
     */
    boolean deleteWithCheck(Long warehouseId);

}
