package com.jjx.production;

import com.jjx.production.service.impl.ProductionReworkTraceServiceImpl;
import com.jjx.quality.domain.entity.QualityLot;
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

    private static String pendingTaskText(String taskStatus, boolean hasWorkerTasks) {
        return ProductionReworkTraceServiceImpl.buildStatusText("PROCESSING", 0, bd("2"), bd("0"), bd("0"),
                null, "WO-1-P03-T01", taskStatus, "一级负责人", hasWorkerTasks);
    }

    @Test
    void waitingToStartTellsWorkerWhatToDo() {
        String s = pendingTaskText("PENDING", false);
        assertTrue(s.contains("WO-1-P03-T01"), s);
        assertTrue(s.contains("派工"), s);
        assertTrue(s.contains("2"), s);
    }

    @Test
    void dispatchedTaskPointsWorkerToStartAndReport() {
        String s = pendingTaskText("ACTIVE", true);
        assertTrue(s.contains("已派给执行人"), s);
        assertTrue(s.contains("开工"), s);
        assertTrue(s.contains("报工并完成审批"), s);
    }

    @Test
    void executingShowsReportedProgress() {
        assertTrue(text("PROCESSING", 2, "2", "0", null, false).contains("0/2"));
        String half = text("PROCESSING", 2, "2", "1", null, false);
        assertTrue(half.contains("1/2"), half);
    }

    @Test
    void pausedKeepsWorkerInformed() {
        assertTrue(text("PROCESSING", 3, "2", "0", null, false).contains("已暂停"));
    }

    @Test
    void finishedReworkPointsToReinspection() {
        assertTrue(text("PROCESSING", 4, "2", "2", "0", false).contains("复检"));
        assertTrue(text("PROCESSING", 4, "2", "2", "0", true).contains("待检"));
    }

    @Test
    void passedReinspectionPointsBackToNcrClosure() {
        QualityLot lot = new QualityLot();
        lot.setLotNo("FQC-1");
        lot.setResult("pass");
        String s = ProductionReworkTraceServiceImpl.buildStatusText("PROCESSING", 4,
                bd("2"), bd("2"), bd("2"), lot, "WO-1-P03-T01", "COMPLETED", "负责人", true);
        assertTrue(s.contains("FQC-1"), s);
        assertTrue(s.contains("推进返工闭环"), s);
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
        String s = ProductionReworkTraceServiceImpl.buildStatusText("PROCESSING", 0,
                bd("2.0000"), bd("0"), bd("0"), null, "WO-1-P03-T01", "PENDING", "负责人", false);
        assertTrue(s.contains("2 件"), s);
        assertTrue(!s.contains("2.0000"), s);
    }
}
