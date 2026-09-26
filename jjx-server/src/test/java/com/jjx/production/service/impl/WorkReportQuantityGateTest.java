package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkReportQuantityGateTest {

    @Test
    void reportCannotExceedItsCurrentTaskAllowance() {
        assertDoesNotThrow(() -> WorkReportActionServiceImpl.validateReportWithinRemaining(
                new BigDecimal("3"), new BigDecimal("3")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> WorkReportActionServiceImpl.validateReportWithinRemaining(
                        new BigDecimal("3.01"), new BigDecimal("3")));
        assertTrue(error.getMessage().contains("报工数量超过任务当前剩余"));
    }
}
