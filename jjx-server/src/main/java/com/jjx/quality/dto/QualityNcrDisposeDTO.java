package com.jjx.quality.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 不良处置入参 —— dev-20260917-003
 */
@Data
public class QualityNcrDisposeDTO {

    /** REWORK 返工 / CONCESSION 让步接收 / SCRAP 报废 */
    private String actionType;

    private BigDecimal quantity;

    /** 让步接收：客户是否已确认 */
    private Boolean customerConfirmed;

    private String approvedBy;
    private String resultRemark;
    private String operatorName;
}
