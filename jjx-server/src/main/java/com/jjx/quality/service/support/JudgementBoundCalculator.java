package com.jjx.quality.service.support;

import java.math.BigDecimal;

/**
 * 判定「可合格上界」纯函数（dev-20260923-021 一期）。
 *
 * <p>业内口径（ISO 9001:2015 §8.7 + SAP QM 使用决策 + 数量守恒）：
 * <b>已判报废的数量不会因为"换版本/重判"回到良品</b>；回收的唯一合法路径是返工 + 复检合格。
 *
 * <pre>
 *   可合格上界 = 批批量 − Σ(链上已报废且未回收) − Σ(让步接收但客户未确认)
 * </pre>
 *
 * 设计为纯静态函数，便于无 DB 单测（见 {@code JudgementBoundCalculatorTest}）。
 * 上界数据由调用方实时按 {@code quality_ncr_action} 汇总（不落冗余列，避免第二真源）。
 */
public final class JudgementBoundCalculator {

    private JudgementBoundCalculator() {
    }

    /** 空值按 0；负值按 0（防御脏数据） */
    public static BigDecimal safe(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    /**
     * 可合格上界。
     *
     * @param lotQuantity            批批量
     * @param scrappedDone           链上「已报废(SCRAP) 且 DONE 且未回收」量
     * @param concessionUnconfirmed  链上「让步接收(CONCESSION) 且 DONE 且客户未确认」量
     * @return 上界（不小于 0；批量缺失时返回 0）
     */
    public static BigDecimal upperBound(BigDecimal lotQuantity,
                                        BigDecimal scrappedDone,
                                        BigDecimal concessionUnconfirmed) {
        BigDecimal base = safe(lotQuantity);
        BigDecimal upper = base.subtract(safe(scrappedDone)).subtract(safe(concessionUnconfirmed));
        return upper.signum() < 0 ? BigDecimal.ZERO : upper;
    }

    /**
     * 建议预填的不良量 = 批量 − 上界（即链上不可回收量在本版的体现）。
     * 用于"复检默认不良量"，让判定弹窗打开就是正确口径，而不是 100/0。
     */
    public static BigDecimal suggestedFail(BigDecimal lotQuantity, BigDecimal upperBound) {
        BigDecimal fail = safe(lotQuantity).subtract(safe(upperBound));
        return fail.signum() < 0 ? BigDecimal.ZERO : fail;
    }

    /** 是否需要警示（上界 < 批量，说明链上有不可回收量） */
    public static boolean needWarning(BigDecimal lotQuantity, BigDecimal upperBound) {
        return safe(upperBound).compareTo(safe(lotQuantity)) < 0;
    }
}
