package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 入库单新增参数DTO
 */
@Data
public class InboundSaveDTO {

    private String inboundType;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long warehouseId;
    private Long locationId;
    private Long supplierId;
    private String supplierName;
    private LocalDate inboundDate;
    private Long inspectorId;
    private String inspectorName;
    private String inspectionResult;
    private String inspectionRemark;
    private String remark;
    private List<InboundItemSaveDTO> items;
}

/**
 * 入库单明细新增参数DTO
 */
@Data
class InboundItemSaveDTO {
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private Long locationId;
    private BigDecimal qualifiedQuantity;
    private BigDecimal rejectedQuantity;
    private String rejectReason;
    private Integer sortOrder;
    private String remark;
}
