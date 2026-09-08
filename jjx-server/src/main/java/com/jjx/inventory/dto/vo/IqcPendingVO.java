package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** IQC 采购收货单及检验进度。 */
@Data
public class IqcPendingVO {

    private Long inboundId;
    private String inboundNo;
    private String supplierName;
    private BigDecimal totalQuantity;
    private Long materialCount;
    private Long inspectedCount;
    private Long pendingReviewCount;
    private Long approvedCount;
    private Long failRowCount;
    private Integer orderStatus;
    private String inspectionResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
