package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细视图对象VO
 */
@Data
public class InboundItemVO {
    private Long inboundItemId;
    private Long inboundId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal sampledQuantity;
    private Long inspectionId;
    /** 检验批 ID（新质检模型 quality_lot；dev-20260922-009 补：inspectionId 已置空，前端需靠它取检验批） */
    private Long lotId;
    private String inspectionResult;
    private String disposition;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String batchNo;
    private Long iqcBatchId;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private Long locationId;
    private String locationName;
    private BigDecimal qualifiedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal postedQuantity;
    private String rejectReason;
    private Integer sortOrder;
    private String remark;
}
