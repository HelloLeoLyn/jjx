package com.jjx.inventory.dto.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 出库单明细视图对象VO
 */
@Data
public class OutboundItemVO {
    private Long stockItemId;
    private String rollNo;
    private Long reversalOfItemId;

    private Long outboundItemId;
    private Long outboundId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String batchNo;
    private Long locationId;
    private String locationName;
    private Integer sortOrder;
    private String remark;
    /** 领料行对应的 BOM 明细（产品BOM或制造BOM） */
    private Long sourceBomItemId;
    private BigDecimal coverageQuantity;
    private String processName;
    private BigDecimal moduleQty;
    private BigDecimal baseQty;
    private BigDecimal pitchMm;
    private BigDecimal qtyNeeded;
    private BigDecimal availableQuantity;
    private Integer level;
    private String parentRowKey;
    private String rowKey;
}
