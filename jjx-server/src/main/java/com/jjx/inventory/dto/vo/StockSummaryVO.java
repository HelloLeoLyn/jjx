package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存汇总视图对象VO
 */
@Data
public class StockSummaryVO {

    /**
     * 总库存数量
     */
    private BigDecimal totalQuantity;

    /**
     * 总预留数量
     */
    private BigDecimal totalReservedQuantity;

    /**
     * 总可用数量
     */
    private BigDecimal totalAvailableQuantity;

    /**
     * 总库存成本
     */
    private BigDecimal totalCost;

    /**
     * 物料种类数量
     */
    private Integer materialCount;

    /**
     * 仓库数量
     */
    private Integer warehouseCount;

    /**
     * 批次数量
     */
    private Integer batchCount;

    /**
     * 低库存物料数量（低于安全库存）
     */
    private Integer lowStockMaterialCount;

    /**
     * 临期库存数量（30天内过期）
     */
    private Integer expiringStockCount;

    /**
     * 呆滞库存数量（180天未出库）
     */
    private Integer obsoleteStockCount;

    /**
     * 过期库存数量
     */
    private Integer expiredStockCount;

    /**
     * 平均库存周转率
     */
    private BigDecimal avgTurnoverRate;

    /**
     * 平均库存天数
     */
    private BigDecimal avgInventoryDays;

    /**
     * 按仓库统计
     */
    private java.util.List<WarehouseStockSummary> warehouseSummaries;

    /**
     * 按物料类型统计
     */
    private java.util.List<MaterialTypeStockSummary> materialTypeSummaries;

    /**
     * 仓库库存汇总
     */
    @Data
    public static class WarehouseStockSummary {
        private Long warehouseId;
        private String warehouseCode;
        private String warehouseName;
        private BigDecimal quantity;
        private BigDecimal cost;
        private Integer materialCount;
    }

    /**
     * 物料类型库存汇总
     */
    @Data
    public static class MaterialTypeStockSummary {
        private String materialType;
        private String materialTypeName;
        private BigDecimal quantity;
        private BigDecimal cost;
        private Integer materialCount;
    }
}
