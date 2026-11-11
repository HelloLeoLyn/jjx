package com.jjx.production.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读需求快照（dev-20261111-006）。
 *
 * <p>直接承载 engineering_bom / engineering_bom_item 的服务端读取结果。loss_rate 列在库中是
 * decimal(5,2)，旧实体为 Integer，本快照按 BigDecimal 读取，避免 0.50 被静默截断为 0。</p>
 *
 * <p>快照只用于 preview 计算，不代表持久化模型，也不写库存/工单/流水。</p>
 */
@Data
public class ManufacturingDemandSnapshot {

    private Long bomId;
    private String bomCode;
    private String bomName;
    private String bomType;
    private Long productId;
    private String outputType;
    private Long outputId;
    private Integer approveStatus;
    private Integer isCurrent;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private Long itemId;
        private Long bomId;
        private Long parentMaterialId;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String unit;
        private BigDecimal moduleQty;
        private BigDecimal baseQty;
        /** 库中 decimal(5,2)，单位是百分数。 */
        private BigDecimal lossRate;
        private BigDecimal minIssueQty;
        private String sourceType;
        private Long subBomId;
        private BigDecimal unitFactor;
        private String unitFactorBasis;
        private String unitFactorSource;
        private BigDecimal widthMm;
        private BigDecimal lengthMm;
        private Integer itemOrder;
        private Integer reviewStatus;
    }
}
