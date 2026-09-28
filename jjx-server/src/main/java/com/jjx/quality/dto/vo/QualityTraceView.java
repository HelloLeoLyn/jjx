package com.jjx.quality.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 质量链路统一只读口径；只从现有事实派生，不落库、不参与业务写入。 */
@Data
public class QualityTraceView {
    private Long lotId;
    private String lotNo;
    private String lotType;
    private String sourceNo;
    private String upstreamSourceNo;
    private String parentLotNo;
    private String relationshipMode;
    private BigDecimal scopeQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal inspectedQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal defectiveQuantity;
    private BigDecimal reinspectionQuantity;
    private BigDecimal disposedQuantity;
    private BigDecimal remainingDispositionQuantity;
    private BigDecimal confirmedInboundQuantity;
    private String dispositionStatus;
}
