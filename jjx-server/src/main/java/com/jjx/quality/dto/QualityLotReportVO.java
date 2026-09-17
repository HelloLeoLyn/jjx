package com.jjx.quality.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验批报告（QR-037 进料检验报告 / QR-039 成品检验报告）—— dev-20260917-012
 * 数据源：quality_lot + quality_lot_item + 抽样方案 + 来源单据（入库单/工单/销售订单）
 */
@Data
public class QualityLotReportVO {

    /** 表单编号：JJX-QR-037 / JJX-QR-039 */
    private String recordNo;
    private String reportTitle;

    private Long lotId;
    private String lotNo;
    private String lotType;

    /** 来源单号：来料=入库单号，成品=工单号 */
    private String sourceNo;
    /** 供方（来料）/ 客户（成品） */
    private String partnerName;

    private String materialCode;
    private String materialName;
    private String productCode;
    private String productName;
    private String specification;
    private String batchNo;

    private BigDecimal lotQuantity;
    private BigDecimal inspectedQuantity;
    private BigDecimal passQuantity;
    private BigDecimal failQuantity;

    /** 抽样（AQL） */
    private BigDecimal sampleQuantity;
    private BigDecimal aqlValue;
    private String inspectionLevel;
    private BigDecimal acceptNumber;
    private BigDecimal rejectNumber;
    private String samplingPlanName;

    private String result;
    private String resultLabel;
    private String version;

    private String inspector;
    private LocalDateTime inspectTime;
    private String remark;

    /** 不良汇总（台账） */
    private BigDecimal defectQuantity;
    private BigDecimal defectCr;
    private BigDecimal defectMa;
    private BigDecimal defectMi;
    private BigDecimal defectDisposed;

    private List<Item> items;

    @Data
    public static class Item {
        private String checkItem;
        private String standard;
        private String inspectionMethod;
        private String equipment;
        /** 逐件实测值（原样，前端按 | 拆分展示 SAMPLE1..n） */
        private String sampleValues;
        private BigDecimal crQuantity;
        private BigDecimal maQuantity;
        private BigDecimal miQuantity;
        private String result;
        private String remark;
    }
}
