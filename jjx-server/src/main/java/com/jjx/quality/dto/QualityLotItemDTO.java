package com.jjx.quality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验项目入参/出参 —— dev-20260917-001
 *
 * 2026-09-21：加 @JsonIgnoreProperties(ignoreUnknown = true) —— 前端可能带展示用字段
 * （如 category 分组），Jackson 默认对未知字段报错（Unrecognized field "category"），
 * 会让「保存录入」整个请求 400。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
