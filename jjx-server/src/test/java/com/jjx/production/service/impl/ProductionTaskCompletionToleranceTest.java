package com.jjx.production.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionTaskCompletionToleranceTest {

    @Test
    void completed99Point99Of100HasZeroRemainingAndMeetsCompletionTolerance() {
        BigDecimal taskQuantity = new BigDecimal("100.00");
        BigDecimal subtreeCompleted = new BigDecimal("99.99");
        BigDecimal remaining = taskQuantity.subtract(subtreeCompleted);

        assertTrue(ProductionTaskServiceImpl.withinCompletionTolerance(
                subtreeCompleted, taskQuantity));
        assertEquals(0, ProductionTaskServiceImpl.floorCompletionZero(remaining)
                .compareTo(BigDecimal.ZERO));
    }
}
