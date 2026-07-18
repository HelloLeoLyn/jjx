package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
@Data
public class SupplierEvaluationDTO{
    /**
     * 供应商ID（更新时使用）
     */
    private Long supplierId;
    /**
    * 评估总分
    */
    private BigDecimal evaluationScore;

    /**
    * 质量评分
    */
    private BigDecimal qualityScore;

    /**
    * 交期评分
    */
    private BigDecimal deliveryScore;

    /**
    * 价格评分
    */
    private BigDecimal priceScore;

    /**
    * 最后评估日期
    */
    private LocalDate lastEvaluationDate;

}
