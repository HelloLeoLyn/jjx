package com.jjx.product.service;

import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.entity.ProductBomItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * BOM成本计算引擎
 * 计算BOM的物料成本、人工成本、制造费用和总成本
 */
@Component
public class BOMCostCalculator {

    /**
     * 计算BOM总成本
     * @param bom BOM信息
     * @param items BOM明细
     * @param materialPrices 物料单价映射表
     * @param laborRates 人工费率映射表
     * @return 成本计算结果
     */
    public BOMCostResult calculateTotalCost(ProductBom bom, List<ProductBomItem> items,
                                            Map<Long, BigDecimal> materialPrices,
                                            Map<Long, BigDecimal> laborRates) {
        BOMCostResult result = new BOMCostResult();

        // 计算物料成本
        BigDecimal materialCost = calculateMaterialCost(items, materialPrices);
        result.setMaterialCost(materialCost);

        // 计算人工成本
        BigDecimal laborCost = calculateLaborCost(items, laborRates);
        result.setLaborCost(laborCost);

        // 计算制造费用（按物料成本的百分比）
        BigDecimal manufacturingOverhead = calculateManufacturingOverhead(materialCost);
        result.setManufacturingOverhead(manufacturingOverhead);

        // 计算总成本
        BigDecimal totalCost = materialCost.add(laborCost).add(manufacturingOverhead);
        result.setTotalCost(totalCost);

        // 计算单位成本
//        if (bom.getStandardQuantity() != null && bom.getStandardQuantity().compareTo(BigDecimal.ZERO) > 0) {
//            BigDecimal unitCost = totalCost.divide(bom.getStandardQuantity(), 4, RoundingMode.HALF_UP);
//            result.setUnitCost(unitCost);
//        }

        // 设置其他信息
        result.setBomId(bom.getBomId());
        result.setBomCode(bom.getBomCode());
        result.setBomVersion(bom.getBomVersion());
        result.setItemCount(items.size());

        return result;
    }

    /**
     * 计算物料成本
     * @param items BOM明细
     * @param materialPrices 物料单价映射表
     * @return 物料成本
     */
    private BigDecimal calculateMaterialCost(List<ProductBomItem> items, Map<Long, BigDecimal> materialPrices) {
        BigDecimal totalMaterialCost = BigDecimal.ZERO;

        for (ProductBomItem item : items) {
            BigDecimal itemCost = item.calculateAmount(item.getUnitPrice());
//            BigDecimal itemCost = unitPrice.multiply(effectiveQuantity);
            totalMaterialCost = totalMaterialCost.add(itemCost);
        }

        return totalMaterialCost.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算人工成本
     * @param items BOM明细
     * @param laborRates 人工费率映射表
     * @return 人工成本
     */
    private BigDecimal calculateLaborCost(List<ProductBomItem> items, Map<Long, BigDecimal> laborRates) {
        BigDecimal totalLaborCost = BigDecimal.ZERO;

//        for (ProductBomItem item : items) {
//            BigDecimal laborRate = getLaborRate(item.getProcessId(), laborRates);
//            BigDecimal laborHours = item.getLaborHours() != null ? item.getLaborHours() : BigDecimal.ZERO;
//
//            BigDecimal itemLaborCost = laborRate.multiply(laborHours);
//            totalLaborCost = totalLaborCost.add(itemLaborCost);
//        }

        return totalLaborCost.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算制造费用
     * @param materialCost 物料成本
     * @return 制造费用
     */
    private BigDecimal calculateManufacturingOverhead(BigDecimal materialCost) {
        // 制造费用 = 物料成本 × 制造费用率（默认15%）
        BigDecimal overheadRate = new BigDecimal("0.15");
        return materialCost.multiply(overheadRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取物料单价
     * @param materialId 物料ID
     * @param materialPrices 物料单价映射表
     * @return 物料单价
     */
    private BigDecimal getMaterialUnitPrice(Long materialId, Map<Long, BigDecimal> materialPrices) {
        if (materialId == null || materialPrices == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal price = materialPrices.get(materialId);
        return price != null ? price : BigDecimal.ZERO;
    }

    /**
     * 获取人工费率
     * @param processId 工序ID
     * @param laborRates 人工费率映射表
     * @return 人工费率
     */
    private BigDecimal getLaborRate(Long processId, Map<Long, BigDecimal> laborRates) {
        if (processId == null || laborRates == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = laborRates.get(processId);
        return rate != null ? rate : BigDecimal.ZERO;
    }

    /**
     * 计算BOM成本差异
     * @param standardCost 标准成本
     * @param actualCost 实际成本
     * @return 成本差异
     */
    public CostVariance calculateCostVariance(BigDecimal standardCost, BigDecimal actualCost) {
        CostVariance variance = new CostVariance();
        variance.setStandardCost(standardCost);
        variance.setActualCost(actualCost);

        // 计算差异金额
        BigDecimal varianceAmount = actualCost.subtract(standardCost);
        variance.setVarianceAmount(varianceAmount);

        // 计算差异率
        if (standardCost.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal varianceRate = varianceAmount.divide(standardCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            variance.setVarianceRate(varianceRate);
        }

        // 判断差异类型
        if (varianceAmount.compareTo(BigDecimal.ZERO) > 0) {
            variance.setVarianceType("超支");
        } else if (varianceAmount.compareTo(BigDecimal.ZERO) < 0) {
            variance.setVarianceType("节约");
        } else {
            variance.setVarianceType("持平");
        }

        return variance;
    }

    /**
     * 计算BOM成本构成比例
     * @param result 成本计算结果
     * @return 成本构成比例
     */
    public CostComposition calculateCostComposition(BOMCostResult result) {
        CostComposition composition = new CostComposition();
        BigDecimal totalCost = result.getTotalCost();

        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            // 计算物料成本比例
            BigDecimal materialRatio = result.getMaterialCost()
                    .divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            composition.setMaterialRatio(materialRatio);

            // 计算人工成本比例
            BigDecimal laborRatio = result.getLaborCost()
                    .divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            composition.setLaborRatio(laborRatio);

            // 计算制造费用比例
            BigDecimal overheadRatio = result.getManufacturingOverhead()
                    .divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            composition.setOverheadRatio(overheadRatio);
        }

        composition.setTotalCost(totalCost);
        return composition;
    }

    /**
     * BOM成本计算结果
     */
    public static class BOMCostResult {
        private Long bomId;
        private String bomCode;
        private String bomVersion;
        private int itemCount;
        private BigDecimal materialCost;
        private BigDecimal laborCost;
        private BigDecimal manufacturingOverhead;
        private BigDecimal totalCost;
        private BigDecimal unitCost;

        // Getters and Setters
        public Long getBomId() {
            return bomId;
        }

        public void setBomId(Long bomId) {
            this.bomId = bomId;
        }

        public String getBomCode() {
            return bomCode;
        }

        public void setBomCode(String bomCode) {
            this.bomCode = bomCode;
        }

        public String getBomVersion() {
            return bomVersion;
        }

        public void setBomVersion(String bomVersion) {
            this.bomVersion = bomVersion;
        }

        public int getItemCount() {
            return itemCount;
        }

        public void setItemCount(int itemCount) {
            this.itemCount = itemCount;
        }

        public BigDecimal getMaterialCost() {
            return materialCost;
        }

        public void setMaterialCost(BigDecimal materialCost) {
            this.materialCost = materialCost;
        }

        public BigDecimal getLaborCost() {
            return laborCost;
        }

        public void setLaborCost(BigDecimal laborCost) {
            this.laborCost = laborCost;
        }

        public BigDecimal getManufacturingOverhead() {
            return manufacturingOverhead;
        }

        public void setManufacturingOverhead(BigDecimal manufacturingOverhead) {
            this.manufacturingOverhead = manufacturingOverhead;
        }

        public BigDecimal getTotalCost() {
            return totalCost;
        }

        public void setTotalCost(BigDecimal totalCost) {
            this.totalCost = totalCost;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
        }

        @Override
        public String toString() {
            return "BOMCostResult{" +
                    "bomId=" + bomId +
                    ", bomCode='" + bomCode + '\'' +
                    ", bomVersion='" + bomVersion + '\'' +
                    ", itemCount=" + itemCount +
                    ", materialCost=" + materialCost +
                    ", laborCost=" + laborCost +
                    ", manufacturingOverhead=" + manufacturingOverhead +
                    ", totalCost=" + totalCost +
                    ", unitCost=" + unitCost +
                    '}';
        }
    }

    /**
     * 成本差异
     */
    public static class CostVariance {
        private BigDecimal standardCost;
        private BigDecimal actualCost;
        private BigDecimal varianceAmount;
        private BigDecimal varianceRate;
        private String varianceType;

        // Getters and Setters
        public BigDecimal getStandardCost() {
            return standardCost;
        }

        public void setStandardCost(BigDecimal standardCost) {
            this.standardCost = standardCost;
        }

        public BigDecimal getActualCost() {
            return actualCost;
        }

        public void setActualCost(BigDecimal actualCost) {
            this.actualCost = actualCost;
        }

        public BigDecimal getVarianceAmount() {
            return varianceAmount;
        }

        public void setVarianceAmount(BigDecimal varianceAmount) {
            this.varianceAmount = varianceAmount;
        }

        public BigDecimal getVarianceRate() {
            return varianceRate;
        }

        public void setVarianceRate(BigDecimal varianceRate) {
            this.varianceRate = varianceRate;
        }

        public String getVarianceType() {
            return varianceType;
        }

        public void setVarianceType(String varianceType) {
            this.varianceType = varianceType;
        }

        @Override
        public String toString() {
            return "CostVariance{" +
                    "standardCost=" + standardCost +
                    ", actualCost=" + actualCost +
                    ", varianceAmount=" + varianceAmount +
                    ", varianceRate=" + varianceRate +
                    ", varianceType='" + varianceType + '\'' +
                    '}';
        }
    }

    /**
     * 成本构成比例
     */
    public static class CostComposition {
        private BigDecimal materialRatio;
        private BigDecimal laborRatio;
        private BigDecimal overheadRatio;
        private BigDecimal totalCost;

        // Getters and Setters
        public BigDecimal getMaterialRatio() {
            return materialRatio;
        }

        public void setMaterialRatio(BigDecimal materialRatio) {
            this.materialRatio = materialRatio;
        }

        public BigDecimal getLaborRatio() {
            return laborRatio;
        }

        public void setLaborRatio(BigDecimal laborRatio) {
            this.laborRatio = laborRatio;
        }

        public BigDecimal getOverheadRatio() {
            return overheadRatio;
        }

        public void setOverheadRatio(BigDecimal overheadRatio) {
            this.overheadRatio = overheadRatio;
        }

        public BigDecimal getTotalCost() {
            return totalCost;
        }

        public void setTotalCost(BigDecimal totalCost) {
            this.totalCost = totalCost;
        }

        @Override
        public String toString() {
            return "CostComposition{" +
                    "materialRatio=" + materialRatio +
                    ", laborRatio=" + laborRatio +
                    ", overheadRatio=" + overheadRatio +
                    ", totalCost=" + totalCost +
                    '}';
        }
    }
}
