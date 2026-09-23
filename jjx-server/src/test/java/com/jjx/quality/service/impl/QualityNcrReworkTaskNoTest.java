package com.jjx.quality.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 返工任务号生成单测 —— dev-20260923-035。
 *
 * 背景：返工任务原来叫 NCR-4-REWORK，在派工管理/任务列表里看不出属于哪张工单；
 * 现在与正常任务同构（WO-&lt;工单号&gt;-P&lt;2位工序序&gt;-T&lt;2位任务序&gt;；T 位 3→2 由 dev-20260923-029 统一）。
 */
class QualityNcrReworkTaskNoTest {

    @Test
    void buildsWorkOrderStyleTaskNo() {
        assertEquals("WO-PL260923001-01-P03-T01",
                QualityNcrServiceImpl.reworkTaskNo("WO-PL260923001-01", 3, 1L));
    }

    @Test
    void padsProcessOrderAndSeq() {
        assertEquals("WO-X-P01-T02", QualityNcrServiceImpl.reworkTaskNo("WO-X", 1, 2L));
        assertEquals("WO-X-P12-T10", QualityNcrServiceImpl.reworkTaskNo("WO-X", 12, 10L));
    }

    @Test
    void fallsBackWhenOrderNoMissing() {
        assertEquals("REWORK-P03-T01", QualityNcrServiceImpl.reworkTaskNo(null, 3, 1L));
        assertEquals("REWORK-P00-T01", QualityNcrServiceImpl.reworkTaskNo("  ", null, 1L));
    }
}
