package com.jjx.quality.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验批判定入参 —— dev-20260917-007
 */
@Data
public class QualityLotJudgeDTO {

    /** 本次检验数量（成品=本批全检数量；来料=抽检数量） */
    private BigDecimal inspectedQuantity;
    private BigDecimal passQuantity;
    private BigDecimal failQuantity;

    private String defectReason;

    /** 可空：不传时按 不良>0 → fail，否则 pass */
    private String result;
}
