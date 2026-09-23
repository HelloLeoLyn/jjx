package com.jjx.quality.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判定「可合格上界」纯函数单测 —— dev-20260923-021 一期。
 * 复刻 2026-09-23 实测缺陷：批 100 → 报废 2（DONE）→ 复检再判 100 全合格 → 库存虚高 2。
 */
class JudgementBoundCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void 有报废时上界应扣减() {
        // 批 100，链上报废 2 未回收 → 上界 98（即本案：不允许把 2 件洗回良品）
        assertEquals(0, JudgementBoundCalculator.upperBound(bd("100"), bd("2"), BigDecimal.ZERO)
                .compareTo(bd("98")));
    }

    @Test
    void 让步未确认也应扣减() {
        assertEquals(0, JudgementBoundCalculator.upperBound(bd("100"), bd("2"), bd("3"))
                .compareTo(bd("95")));
    }

    @Test
    void 无处置时上界等于批量_不影响正常批() {
        assertEquals(0, JudgementBoundCalculator.upperBound(bd("100"), null, null)
                .compareTo(bd("100")));
        assertFalse(JudgementBoundCalculator.needWarning(bd("100"), bd("100")));
    }

    @Test
    void 扣减超过批量时应收敛为0_不出现负数() {
        assertEquals(0, JudgementBoundCalculator.upperBound(bd("10"), bd("12"), bd("1"))
                .compareTo(BigDecimal.ZERO));
    }

    @Test
    void 批量缺失时上界为0() {
        assertEquals(0, JudgementBoundCalculator.upperBound(null, bd("1"), null)
                .compareTo(BigDecimal.ZERO));
    }

    @Test
    void 建议预填不良量等于批量减上界() {
        BigDecimal upper = JudgementBoundCalculator.upperBound(bd("100"), bd("2"), BigDecimal.ZERO);
        assertEquals(0, JudgementBoundCalculator.suggestedFail(bd("100"), upper).compareTo(bd("2")));
        assertEquals(0, JudgementBoundCalculator.suggestedFail(bd("100"), bd("100")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void 需要警示的判定() {
        assertTrue(JudgementBoundCalculator.needWarning(bd("100"), bd("98")));
        assertFalse(JudgementBoundCalculator.needWarning(bd("100"), bd("100")));
    }

    @Test
    void 负值按0处理() {
        assertEquals(0, JudgementBoundCalculator.upperBound(bd("100"), bd("-5"), bd("-3"))
                .compareTo(bd("100")));
    }
}
