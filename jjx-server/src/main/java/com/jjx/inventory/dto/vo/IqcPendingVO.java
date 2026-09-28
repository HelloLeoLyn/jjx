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
    private String sourceNo;
    private String supplierName;
    private BigDecimal totalQuantity;
    private Long materialCount;
    private Long inspectedCount;
    private Long pendingReviewCount;
    private Long approvedCount;
    private Long failRowCount;
    /** 当前工作检验批待录入行数（含驳回，排除返工复检）。 */
    private Long pendingInspectionCount;
    private Long pendingReinspectionCount;
    private BigDecimal remainingDispositionQuantity;
    private Long pendingScrapCount;
    private Long pendingReworkCount;
    private Integer orderStatus;
    private String inspectionResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
