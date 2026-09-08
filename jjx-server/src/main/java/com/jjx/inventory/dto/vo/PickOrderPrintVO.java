package com.jjx.inventory.dto.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** JJX-QR-031 领料单纸版打印数据。 */
@Data
public class PickOrderPrintVO {
    private Long outboundId;
    private String outboundNo;
    private String machineModel;
    private String productName;
    private BigDecimal orderQuantity;
    private LocalDate deliveryDate;
    private LocalDate preparedDate;
    private String preparedBy;
    private String recordNo;
    private List<PickOrderPrintItemVO> items;
}
