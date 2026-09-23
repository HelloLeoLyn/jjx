package com.jjx.quality.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不良单结案判据单测 —— dev-20260923-046（看板 2105 复核发现的 P2 门禁缺口）。
 *
 * <p>口径：处置量已覆盖不良量 **且没有未关闭的 CAPA** 才能结案；三条结案路径
 * （处置完成 / 复检更正同步台账 / 返工完成）共用 {@link QualityNcrServiceImpl#canCloseNcr}。</p>
 */
class QualityNcrCloseGateTest {

    @Test
    void closesOnlyWhenFullyDisposedAndNoOpenCapa() {
        assertTrue(QualityNcrServiceImpl.canCloseNcr(new BigDecimal("2"), new BigDecimal("2"), 0));
        assertTrue(QualityNcrServiceImpl.canCloseNcr(new BigDecimal("3"), new BigDecimal("2"), 0));
    }

    @Test
    void blocksWhenDisposalIncomplete() {
        assertFalse(QualityNcrServiceImpl.canCloseNcr(new BigDecimal("1"), new BigDecimal("2"), 0));
        assertFalse(QualityNcrServiceImpl.canCloseNcr(BigDecimal.ZERO, new BigDecimal("2"), 0));
    }

    @Test
    void blocksWhenOpenCapaExists() {
        assertFalse(QualityNcrServiceImpl.canCloseNcr(new BigDecimal("2"), new BigDecimal("2"), 1));
        assertFalse(QualityNcrServiceImpl.canCloseNcr(new BigDecimal("5"), new BigDecimal("2"), 3));
    }

    @Test
    void treatsNullAsZero() {
        assertTrue(QualityNcrServiceImpl.canCloseNcr(null, BigDecimal.ZERO, 0));
        assertFalse(QualityNcrServiceImpl.canCloseNcr(null, new BigDecimal("1"), 0));
        assertFalse(QualityNcrServiceImpl.canCloseNcr(new BigDecimal("1"), null, 1));
    }
}
