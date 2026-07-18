package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨单明细视图对象VO
 */
@Data
class TransferItemVO {
    private Long transferItemId;
    private Long transferId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal amount;
    private String batchNo;
    private Integer sortOrder;
    private String remark;
}
