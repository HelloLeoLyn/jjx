package com.jjx.quality.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityNcrReplacementAllowanceTest {

    @Test
    void remainingReplacementIsCompletedScrapMinusExistingRequests() {
        assertEquals(new BigDecimal("2"), QualityNcrServiceImpl.remainingReplacementQuantity(
                new BigDecimal("3"), new BigDecimal("1")));
    }

    @Test
    void noScrapOrFullyRequestedScrapHasNoRemainingAllowance() {
        assertEquals(BigDecimal.ZERO, QualityNcrServiceImpl.remainingReplacementQuantity(
                new BigDecimal("1"), new BigDecimal("2")));
        assertEquals(BigDecimal.ZERO, QualityNcrServiceImpl.remainingReplacementQuantity(null, null));
    }
}
