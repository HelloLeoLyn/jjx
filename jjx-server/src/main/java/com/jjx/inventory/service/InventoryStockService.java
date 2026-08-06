package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.inventory.dto.query.StockCheckDTO;
import com.jjx.inventory.dto.query.StockImportDTO;
import com.jjx.inventory.dto.query.StockQueryDTO;
import com.jjx.inventory.dto.vo.StockCheckVO;
import com.jjx.inventory.dto.vo.StockImportResultVO;
import com.jjx.inventory.dto.vo.StockSummaryVO;
import com.jjx.inventory.dto.vo.StockVO;

import java.util.List;
import java.util.Map;

/**
 * 库存汇总 Service 接口
 */
public interface InventoryStockService {

    /**
     * 分页查询库存汇总列表
     */
    IPage<StockVO> page(StockQueryDTO query);

    /**
     * 获取库存汇总信息
     */
    StockSummaryVO getSummary(StockQueryDTO query);

    /**
     * 根据ID查询汇总
     */
    StockVO getById(Long stockId);

    /**
     * 根据物料ID查询汇总
     */
    StockVO getByMaterialId(Long materialId);

    /**
     * 根据仓库查询库存汇总
     */
    List<StockVO> getByWarehouseId(Long warehouseId);

    /**
     * 获取库存预警信息
     */
    Map<String, Object> getAlertInfo();

    /**
     * 查询低库存物料
     */
    List<StockVO> getLowStock();

    /**
     * 查询临期库存
     */
    List<StockVO> getExpiring();

    /**
     * 查询呆滞库存
     */
    List<StockVO> getObsolete();

    /**
     * 获取库存仪表板数据
     */
    Map<String, Object> getDashboardData();

    /**
     * 校验物料并解析仓库库位（用于导入）
     */
    StockCheckVO check(StockCheckDTO checkDTO);

    /**
     * 批量导入库存
     */
    StockImportResultVO batchImport(List<StockImportDTO> list, boolean autoCreateLocation);

    /**
     * 刷新指定物料的汇总数据
     */
    void refreshSummary(Long materialId);
}
