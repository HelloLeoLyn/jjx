package com.jjx.quality.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 建检验批入参 —— dev-20260917-001
 * 来源三选一：来料（sourceType=INBOUND_ITEM + sourceId/sourceItemId）/ 报工批（WORK_REPORT）/ 工序（EXECUTION）
 */
@Data
public class QualityLotCreateDTO {

    /** IQC/IPQC/FQC */
    private String lotType;

    private String sourceType;
    private Long sourceId;
    private Long sourceItemId;

    private Long orderId;
    private Long executionId;

    private Long materialId;
    private String materialCode;
    private String materialName;

    private Long productId;
    private String productCode;
    private String productName;

    private String batchNo;

    /** 本批应检总量（成品=报工产出；来料=本批到货量） */
    private BigDecimal lotQuantity;

    /** 来料抽样信息（可选，来自抽样方案 002） */
    private Long samplingPlanId;
    private BigDecimal sampleQuantity;
    private BigDecimal acceptNumber;
    private BigDecimal rejectNumber;

    /** 复检：来源批 + 版本 */
    private Long parentLotId;
    private Integer version;

    private String remark;

    /** 检验项目（可空：先建批后录入） */
    private List<QualityLotItemDTO> items;
}
