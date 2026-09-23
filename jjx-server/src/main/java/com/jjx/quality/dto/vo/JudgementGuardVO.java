package com.jjx.quality.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 判定护栏（可合格上界）只读视图 —— dev-20260923-021 一期。
 *
 * <p>供判定弹窗预填与展示：上界 = 批批量 − 链上已报废未回收 − 让步未客户确认；
 * 前端据此把「合格」预填为上界、「不良」预填为批量 − 上界，避免用户把已报废量判回良品。
 */
@Data
@Schema(description = "判定护栏：可合格上界与预填建议")
public class JudgementGuardVO {

    @Schema(description = "检验批ID")
    private Long lotId;

    @Schema(description = "检验批号")
    private String lotNo;

    @Schema(description = "批批量")
    private BigDecimal lotQuantity;

    @Schema(description = "链上已报废且未回收量（SCRAP 且 DONE）")
    private BigDecimal scrappedQuantity;

    @Schema(description = "链上让步接收但客户未确认量（CONCESSION 且 DONE 且未确认）")
    private BigDecimal concessionPendingQuantity;

    @Schema(description = "可判合格上限 = 批量 − 已报废 − 让步未确认")
    private BigDecimal upperBound;

    @Schema(description = "建议预填的合格数量（= 上界）")
    private BigDecimal suggestedPass;

    @Schema(description = "建议预填的不良数量（= 批量 − 上界）")
    private BigDecimal suggestedFail;

    @Schema(description = "是否需要警示（上界 < 批量）")
    private Boolean needWarning;

    @Schema(description = "提示文案（可直接展示）")
    private String message;

    @Schema(description = "数据是否可用（false = 护栏降级，前端应退回原行为，不阻塞）")
    private Boolean guardAvailable;
}
