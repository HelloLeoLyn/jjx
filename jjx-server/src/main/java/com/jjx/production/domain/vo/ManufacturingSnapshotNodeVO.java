package com.jjx.production.domain.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 制造历史快照的单个 BOM 层节点（dev-20261111-009 G1）。只读展示，不参与计算。 */
@Data
public class ManufacturingSnapshotNodeVO {

    private Integer level;
    private Integer itemOrder;
    private Long parentMaterialId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal moduleQty;
    private BigDecimal baseQty;
    private BigDecimal lossRate;
    private Long subBomId;
    private Long processId;
    private String processName;
    private String sourceType;
    /** 该层数据来源：BOM_ITEM（BOM 行冗余快照）/ CURRENT_MASTER（缺冗余时才用当前档案）。 */
    private String snapshotSource;
    private List<ManufacturingSnapshotNodeVO> children = new ArrayList<>();
}
