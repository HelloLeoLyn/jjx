package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 出库单新增参数DTO
 */
@Data
public class OutboundSaveDTO {

    private String outboundType;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long warehouseId;
    private Long customerId;
    private String customerName;
    private LocalDate outboundDate;
    private String remark;
    private List<OutboundItemSaveDTO> items;
}

/**
 * 出库单明细新增参数DTO
 */
@Data
class OutboundItemSaveDTO {
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String batchNo;
    private Long locationId;
    private Integer sortOrder;
    private String remark;
}
