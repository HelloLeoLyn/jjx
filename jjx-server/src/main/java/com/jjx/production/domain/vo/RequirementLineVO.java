package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 物料级精确需求/净额结果（只读，dev-20261111-006）。 */
@Data
@Schema(description = "物料精确需求与净额")
public class RequirementLineVO {

    private Long materialId;
    private String materialCode;
    private String materialName;
    /** 库存档案单位，供给抵扣口径。 */
    private String stockUnit;

    private Boolean make;
    private Long subBomId;
    private String bomUnit;
    private String unitFactorExact;
    private String unitFactorBasis;
    private String unitFactorSource;
    private LocalDate demandDate;
    private Long warehouseId;

    /** 未计损耗的理论耗用（权威精确值）。 */
    private String theoreticalQtyExact;
    private BigDecimal theoreticalQtyApprox;
    /** 本层损耗只计一次后的毛需求（权威精确值）。 */
    private String lossAppliedQtyExact;
    private BigDecimal lossAppliedQtyApprox;
    /** 按单位策略只在聚合终点取整的展示值；权威值仍看 exact 字段。 */
    private BigDecimal aggregateQtyDisplay;
    private String aggregateRounding;

    /** 库存/供给分量，均以 stockUnit 计。 */
    private BigDecimal stockOnHand;
    private BigDecimal stockReserved;
    private BigDecimal safeStock;
    private BigDecimal availableForDemand;
    private Boolean safeStockDeducted;
    private BigDecimal productionSupply;
    private BigDecimal purchaseSupply;
    private Boolean materialProductionAdapterApplied;

    private BigDecimal allocatedStock;
    private BigDecimal allocatedProduction;
    private BigDecimal allocatedPurchase;

    /** 缺口 = max(0, 毛需求 − 库存 − 生产供给 − 采购供给)，绝不返回负数。 */
    private String gapQtyExact;
    private BigDecimal gapQtyApprox;
    private BigDecimal gapQtyDisplay;

    /** 最低投料与生产批量分列；没有生产批量依据时 productionBatchQty 为空，不用 min_order_qty 冒充。 */
    private BigDecimal minIssueQty;
    private BigDecimal issueFloorQty;
    private BigDecimal productionBatchQty;
    private BigDecimal issueQty;

    private List<SourceRef> sources = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> limitations = new ArrayList<>();

    /** 单条来源行；保留 BOM 行、路径、方案和分配明细，便于跨分支/跨深度核对。 */
    @Data
    @Schema(description = "需求来源行")
    public static class SourceRef {
        private Long sequence;
        private Long sourceBomId;
        private String sourceBomCode;
        private Long sourceItemId;
        private Integer sourceItemOrder;
        private Long parentNodeId;
        private String sourceType;
        private Long subBomId;
        private String bomUnit;
        private String path;
        private LocalDate demandDate;
        private String theoreticalQtyExact;
        private String appliedQtyExact;
        private String appliedQtyInStockUnitExact;
        private String allocatedStockExact;
        private String allocatedProductionExact;
        private String allocatedPurchaseExact;
        private String gapQtyExact;
    }
}
