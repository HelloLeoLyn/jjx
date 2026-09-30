package com.jjx.production.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * dev-20260930-027：完工门④口径 = 成品检验合格累计 + 让步放行（客户已确认）≥ 计划数量。
 * 依据：2026-09-29 拍板的缺口口径「缺口 = 计划 − 良品 − 让步」；让步件客户已接受，不该卡完工门。
 */
class ProductionOrderCompletionQuantityTest {

    @Test
    void concessionCountsTowardCompletion() {
        // 历史实际：WO260930001 合格 98 + 让步 2 / 计划 100 → 达标
        assertTrue(ProductionOrderServiceImpl.meetsCompletionQuantity(
                new BigDecimal("98"), new BigDecimal("2"), new BigDecimal("100")));
    }

    @Test
    void qualifiedOnlyBelowPlanIsRejected() {
        // 只算合格时 98 < 100 → 不放行（旧口径的病）
        assertFalse(ProductionOrderServiceImpl.meetsCompletionQuantity(
                new BigDecimal("98"), BigDecimal.ZERO, new BigDecimal("100")));
    }

    @Test
    void exactPlanPasses() {
        assertTrue(ProductionOrderServiceImpl.meetsCompletionQuantity(
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100")));
    }

    @Test
    void nullConcessionTreatedAsZero() {
        assertTrue(ProductionOrderServiceImpl.meetsCompletionQuantity(
                new BigDecimal("100"), null, new BigDecimal("100")));
        assertFalse(ProductionOrderServiceImpl.meetsCompletionQuantity(
                new BigDecimal("99"), null, new BigDecimal("100")));
    }

    @Test
    void scrapDoesNotCount() {
        // 报废件不进达标口径：85 合格 + 15 报废 / 计划 100 → 不达标（须补产）
        assertFalse(ProductionOrderServiceImpl.meetsCompletionQuantity(
                new BigDecimal("85"), BigDecimal.ZERO, new BigDecimal("100")));
    }
}
