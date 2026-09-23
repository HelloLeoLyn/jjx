package com.jjx.production;

import com.jjx.production.service.impl.ProductionReworkTraceServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 返工链「一句人话」模板单测 —— dev-20260923-031。
 *
 * 背景：返工在页面上原来只有裸 ID（工序 #12），操作人看不出修到哪一步、下一步该干什么。
 * 这里固化 6 种状态各自的文案，防止以后改坏了又变成"看不懂"。
 */
class ReworkStatusTextTest {

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static String text(String actionStatus, Integer executionStatus, String reworkQty,
                               String reportedQty, String recoveredQty, boolean hasLot) {
        return ProductionReworkTraceServiceImpl.buildStatusText(actionStatus, executionStatus,
                bd(reworkQty), bd(reportedQty), bd(recoveredQty), hasLot);
    }

    @Test
    void waitingToStartTellsWorkerWhatToDo() {
        String s = text("PROCESSING", 0, "2", "0", "0", false);
        assertTrue(s.contains("修回来"), s);
        assertTrue(s.contains("先开工"), s);
        assertTrue(s.contains("2"), s);
    }

    @Test
    void executingShowsReportedProgress() {
        assertTrue(text("PROCESSING", 2, "2", "0", null, false).contains("尚未报工"));
        String half = text("PROCESSING", 2, "2", "1", null, false);
        assertTrue(half.contains("已报 1/2"), half);
    }

    @Test
    void pausedKeepsWorkerInformed() {
        assertTrue(text("PROCESSING", 3, "2", "0", null, false).contains("已暂停"));
    }

    @Test
    void finishedReworkPointsToReinspection() {
        assertTrue(text("PROCESSING", 4, "2", "2", "0", false).contains("复检"));
        assertTrue(text("PROCESSING", 4, "2", "2", "0", true).contains("判定复检批"));
    }

    @Test
    void skippedOrCancelledSaysWhichWayToGo() {
        String s = text("PROCESSING", 5, "2", "0", null, false);
        assertTrue(s.contains("让步接收或报废"), s);
    }

    @Test
    void closedReworkShowsRecoveredQuantity() {
        String full = text("DONE", 4, "2", "2", "2", true);
        assertTrue(full.contains("已回收良品"), full);
        assertTrue(full.contains("2/2"), full);

        String partial = text("DONE", 4, "2", "2", "1", true);
        assertTrue(partial.contains("还差 1 件"), partial);
    }

    @Test
    void noExecutionYetSaysRegisteredOnly() {
        assertTrue(text("PROCESSING", null, "2", "0", null, false).contains("待生成"));
    }

    @Test
    void quantityFormattingHasNoTrailingZeros() {
        assertEquals("把报废/不良的 2 件修回来——先开工这道返工工序", text("PROCESSING", 0, "2.0000", "0", "0", false));
    }
}
