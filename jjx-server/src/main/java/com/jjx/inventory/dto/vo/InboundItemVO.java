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
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private Long locationId;
    private String locationName;
    private BigDecimal qualifiedQuantity;
    private BigDecimal rejectedQuantity;
    private String rejectReason;
    private Integer sortOrder;
    private String remark;
}
