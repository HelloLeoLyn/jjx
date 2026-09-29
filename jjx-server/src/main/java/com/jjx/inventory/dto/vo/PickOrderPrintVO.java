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
    /** 打印抬头（生产领料单 / 打样领料单；dev-20260929-023） */
    private String pickTitle;
    /** 来源类型（work_order / sample） */
    private String sourceType;
    private List<PickOrderPrintItemVO> items;
}
