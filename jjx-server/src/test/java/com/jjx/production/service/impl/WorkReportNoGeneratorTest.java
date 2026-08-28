package com.jjx.production.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkReportNoGeneratorTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 28);

    @Test
    void firstReportOfDayUsesExpectedFormat() {
        String reportNo = WorkReportActionServiceImpl.nextReportNo(REPORT_DATE, null);

        assertEquals("WR-20260828-0001", reportNo);
        assertTrue(reportNo.matches("WR-\\d{8}-\\d{4}"));
    }

    @Test
    void reportsOnSameDayIncrementAndRemainUnique() {
        String first = WorkReportActionServiceImpl.nextReportNo(REPORT_DATE, null);
        String second = WorkReportActionServiceImpl.nextReportNo(REPORT_DATE, first);
        String third = WorkReportActionServiceImpl.nextReportNo(REPORT_DATE, second);

        assertEquals("WR-20260828-0002", second);
        assertEquals("WR-20260828-0003", third);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }
}
