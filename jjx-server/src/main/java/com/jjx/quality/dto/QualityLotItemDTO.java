package com.jjx.quality.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验项目入参/出参 —— dev-20260917-001
 */
@Data
public class QualityLotItemDTO {

    private Long itemId;
    private String checkItem;
    private String standard;
    private String inspectionMethod;
    private String equipment;
    /** 逐件实测值（| 分隔） */
    private String sampleValues;
    private String actualValue;
    private BigDecimal crQuantity;
    private BigDecimal maQuantity;
    private BigDecimal miQuantity;
    /** pass/fail/pending */
    private String result;
    private String remark;
    private Integer sortOrder;
}
