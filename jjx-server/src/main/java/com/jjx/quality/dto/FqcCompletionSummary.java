package com.jjx.quality.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单完工口径汇总（有效 FQC 检验批）—— dev-20260918-014
 *
 * <p>「有效批」= 该工单下 FQC 检验批中**没有后继复检版本**的批（child.parent_lot_id 指向它即被取代）。
 * 与 {@code QualityFinishServiceImpl.syncFinishInbound} 的账口径一致，作为工单完工门禁与成品数量的唯一真源。</p>
 */
@Data
public class FqcCompletionSummary {

    /** 该工单是否存在任何 FQC 检验批 */
    private boolean hasLot;

    /** 有效批张数（无后继复检版本） */
    private int effectiveLotCount;

    /** 有效批中尚未判定的张数（待检） */
    private int pendingCount;

    /** 有效批合格数量累计（判定后） */
    private BigDecimal qualifiedTotal = BigDecimal.ZERO;

    /** 有效批未处置不良合计 = Σ max(0, fail_quantity - disposed_quantity) */
    private BigDecimal undisposedFailQuantity = BigDecimal.ZERO;

    /** 有效批已处置不良合计 = Σ disposed_quantity（dev-20260923-028：工单缺口一句话要用） */
    private BigDecimal disposedFailTotal = BigDecimal.ZERO;

    /** 有效批关联不良单里「已登记报废」合计 = Σ SCRAP DONE 数量（dev-20260923-028） */
    private BigDecimal scrappedTotal = BigDecimal.ZERO;
}
