package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.inventory.dto.query.StockItemQueryDTO;
import com.jjx.inventory.dto.vo.StockItemVO;

import java.util.List;

/**
 * 库存批次明细 Service 接口
 */
public interface InventoryStockItemService {

    /**
     * 分页查询库存明细
     */
    IPage<StockItemVO> page(StockItemQueryDTO query);

    /**
     * 根据ID查询明细
     */
    StockItemVO getById(Long itemId);

    /**
     * 根据物料ID查询所有生效明细
     */
    List<StockItemVO> getByMaterialId(Long materialId);

    /**
     * 根据物料ID和仓库ID查询生效明细
     */
    List<StockItemVO> getByMaterialAndWarehouse(Long materialId, Long warehouseId);
}
