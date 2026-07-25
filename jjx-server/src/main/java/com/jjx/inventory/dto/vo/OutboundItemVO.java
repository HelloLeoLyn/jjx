package com.jjx.inventory.dto.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 出库单明细视图对象VO
 */
@Data
public class OutboundItemVO {
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
}
