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

    /**
     * dev-20260924-004：不良数量 N 小于「检验项目不合格合计 Σ」时的确认开关。
     * 口径：Σ>0 时 N 默认应等于 Σ；N>Σ 直接拒绝（不允许无因不良）；N<Σ 需勾选确认 + 填写说明。
     */
    private Boolean partialConfirmed;

    /** 与 partialConfirmed 配套的说明（必填，留痕） */
    private String partialReason;
}
