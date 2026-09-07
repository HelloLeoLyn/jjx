package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** IQC 待检采购收货单。 */
@Data
public class IqcPendingVO {

    private Long inboundId;
    private String inboundNo;
    private String supplierName;
    private BigDecimal totalQuantity;
    private Long materialCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
