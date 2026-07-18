package com.jjx.inventory.service;

import java.util.List;
import java.util.Map;

/**
 * 库存报表服务接口
 */
public interface InventoryReportService {

    /**
     * 库存周转率统计
     */
    Map<String, Object> calculateTurnover(String startDate, String endDate);

    /**
     * 库存成本统计
     */
    Map<String, Object> calculateCost(String date);

    /**
     * 出入库统计
     */
    Map<String, Object> statInOut(String startDate, String endDate);

    /**
     * ABC分析
     */
    List<Map<String, Object>> abcAnalysis();

    /**
     * 仓库库存统计
     */
    List<Map<String, Object>> warehouseStockStat();

    /**
     * 物料库存趋势
     */
    List<Map<String, Object>> materialTrend(Long materialId, int days);

    /**
     * 库存预警统计
     */
    Map<String, Object> alertStat();

    /**
     * 盘点差异统计
     */
    Map<String, Object> stocktakeDiffStat(String startDate, String endDate);

    /**
     * 调拨统计
     */
    Map<String, Object> transferStat(String startDate, String endDate);

    /**
     * 物料分类库存统计
     */
    List<Map<String, Object>> categoryStockStat();

    /**
     * 呆滞料分析
     */
    List<Map<String, Object>> obsoleteAnalysis(int days);

    /**
     * 保质期分析
     */
    List<Map<String, Object>> expiryAnalysis(int days);

}
