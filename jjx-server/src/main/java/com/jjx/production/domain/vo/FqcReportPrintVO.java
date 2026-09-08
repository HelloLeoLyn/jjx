package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** JJX-QR-039 成品检验报告纸版打印数据。 */
@Data
public class FqcReportPrintVO {
    private Long inspectionId;
    private String inspectionNo;
    private String customerName;
    private BigDecimal orderQuantity;
    private BigDecimal sampleQuantity;
    private String version;
    private String productName;
    private String salesOrderNo;
    private String productionBatchNo;
    private String productCode;
    private String machineModel;
    private LocalDateTime inspectionTime;
    private BigDecimal failQuantity;
    private String result;
    private String resultName;
    private String inspector;
    private String qualitySupervisor;
    private String defectDescription;
    private String recordNo;
    private List<InspectionItemVO> items;
}
