package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 调拨单新增参数DTO
 */
@Data
public class TransferSaveDTO {

    private String transferType;
    private Long fromWarehouseId;
    private Long fromLocationId;
    private Long toWarehouseId;
    private Long toLocationId;
    private LocalDate transferDate;
    private LocalDate expectedDate;
    private String remark;
    private List<TransferItemSaveDTO> items;
}

/**
 * 调拨单明细新增参数DTO
 */
@Data
class TransferItemSaveDTO {
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private String batchNo;
    private Long fromLocationId;
    private Long toLocationId;
    private String remark;
}
