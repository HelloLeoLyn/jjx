package com.jjx.production;

import com.jjx.production.domain.dto.WorkReportReviewDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.service.impl.WorkReportActionServiceImpl;
import com.jjx.system.annotation.Event;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkReportEventContractTest {

    @Test
    void workReportActionsDeclareContextRichEventsAndIdempotencyConditions() throws Exception {
        Event submitted = event("submit", WorkReportSubmitDTO.class, String.class, Long.class);
        assertEquals("production.work-report.submitted", submitted.value());
        assertTrue(Arrays.asList(submitted.params()).contains("receiverId = #result.eventReceiverId"));
        assertContext(submitted);

        Event approved = event("approve", Long.class, WorkReportReviewDTO.class, String.class, Long.class);
        assertEquals("production.work-report.approved", approved.value());
        assertEquals("#result.eventPublished", approved.condition());
        assertTrue(Arrays.asList(approved.params()).contains("receiverId = #result.reporterId"));
        assertContext(approved);

        Event rejected = event("reject", Long.class, WorkReportReviewDTO.class, String.class, Long.class);
        assertEquals("production.work-report.rejected", rejected.value());
        assertEquals("#result.eventPublished", rejected.condition());
        assertTrue(Arrays.asList(rejected.params()).contains("receiverId = #result.reporterId"));
        assertContext(rejected);
    }

    private Event event(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = WorkReportActionServiceImpl.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Event.class);
    }

    private void assertContext(Event event) {
        var params = Arrays.asList(event.params());
        assertTrue(params.contains("orderNo = #result.orderNo"));
        assertTrue(params.contains("executionId = #result.executionId"));
        assertTrue(params.contains("taskId = #result.taskId"));
        assertTrue(params.contains("reportId = #result.reportId"));
        assertTrue(params.contains("qualifiedQuantity = #result.qualifiedQuantity"));
        assertTrue(params.contains("defectiveQuantity = #result.defectiveQuantity"));
    }
}
