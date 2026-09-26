package com.jjx.inventory.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionSupplementAllowanceTest {

    @Test
    void onlyCompletedScrapNotAlreadyRequestedCanBeReplaced() {
        assertEquals(new BigDecimal("1"), InventoryOutboundServiceImpl.replacementAllowance(
                new BigDecimal("2"), new BigDecimal("1")));
    }

    @Test
    void closedNcrDoesNotChangeScrapBasedAllowance() {
        assertEquals(new BigDecimal("1"), InventoryOutboundServiceImpl.replacementAllowance(
                new BigDecimal("1"), BigDecimal.ZERO));
    }

    @Test
    void duplicateRequestsCannotCreateNegativeAllowance() {
        assertEquals(BigDecimal.ZERO, InventoryOutboundServiceImpl.replacementAllowance(
                new BigDecimal("1"), new BigDecimal("2")));
    }
}
