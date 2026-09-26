package com.jjx.production.service.impl;

import com.jjx.production.domain.vo.OrderCompletionStatusVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkOrderOperationQuantityTest {

    @Test
    void serialOperationReportsUseMaximumRatherThanSumForOrderThroughput() {
        var first = operation("10");
        var second = operation("8");

        assertEquals(new BigDecimal("10"), ProductionOperationExecutionServiceImpl
                .maxStandardOperationOutput(List.of(first, second)));
    }

    @Test
    void emptyOperationListHasZeroThroughput() {
        assertEquals(BigDecimal.ZERO, ProductionOperationExecutionServiceImpl.maxStandardOperationOutput(List.of()));
    }

    private static OrderCompletionStatusVO.OperationQuantity operation(String output) {
        var operation = new OrderCompletionStatusVO.OperationQuantity();
        operation.setApprovedOutputQuantity(new BigDecimal(output));
        return operation;
    }
}
