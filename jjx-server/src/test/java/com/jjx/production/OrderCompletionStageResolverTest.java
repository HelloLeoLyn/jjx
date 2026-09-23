package com.jjx.production;

import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.service.impl.OrderCompletionStageResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单完工阶段（含「待补产」）单测 —— dev-20260923-028。
 *
 * 实测背景（WO-PL260923001-01）：计划 200、良品累计 198、报废 2 件已处置、未处置不良 0
 * → 责任人必须被明确告知「报废 2 件，还缺 2 件，去补报」，而不是被误导成"继续检验/补检"。
 */
class OrderCompletionStageResolverTest {

    private static OrderCompletionStageResolver.Input in(Integer status, boolean inboundPending,
                                                         int total, int done, boolean hasLot, int fqcPending,
                                                         String qualified, String undisposed, String scrapped,
                                                         String planned) {
        return new OrderCompletionStageResolver.Input(status, inboundPending, total, done, hasLot, fqcPending,
                bd(qualified), bd(undisposed), bd(scrapped), bd(planned));
    }

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static OrderCompletionStageResolver.Result running(String qualified, String undisposed, String scrapped,
                                                               String planned) {
        return OrderCompletionStageResolver.resolve(
                in(ProductionOrderStatusEnum.IN_PROGRESS.getValue(), false, 2, 2, true, 0,
                        qualified, undisposed, scrapped, planned));
    }

    @Test
    void shortfallWithScrapBecomesPendingSupplement() {
        // 本案实测：报废 2 件已处置 → 待补产，缺口 2，一句话要说清原因与去处
        OrderCompletionStageResolver.Result r = running("198", "0", "2", "200");

        assertEquals(OrderCompletionStageResolver.PENDING_SUPPLEMENT, r.stage());
        assertEquals("待补产", r.label());
        assertEquals(0, new BigDecimal("2").compareTo(r.shortfallQuantity()));
        assertTrue(r.nextAction().contains("报废 2"), r.nextAction());
        assertTrue(r.nextAction().contains("还缺 2"), r.nextAction());
        assertTrue(r.nextAction().contains("补报"), r.nextAction());
    }

    @Test
    void shortfallWithoutScrapStillPointsToSupplement() {
        // 没有报废记录（例如让步/返工前的口径差）也要给缺口，而不是让质检去"补检"
        OrderCompletionStageResolver.Result r = running("198", "0", "0", "200");

        assertEquals(OrderCompletionStageResolver.PENDING_SUPPLEMENT, r.stage());
        assertEquals(0, new BigDecimal("2").compareTo(r.shortfallQuantity()));
        assertTrue(r.nextAction().contains("还缺 2"), r.nextAction());
        assertTrue(!r.nextAction().contains("报废"), r.nextAction());
    }

    @Test
    void undisposedFailKeepsDispositionStageEvenWithShortfall() {
        OrderCompletionStageResolver.Result r = running("98", "2", "0", "100");

        assertEquals(OrderCompletionStageResolver.PENDING_DISPOSITION, r.stage());
        assertTrue(r.nextAction().contains("返工/报废"), r.nextAction());
    }

    @Test
    void qualifiedReachingPlanIsReadyToComplete() {
        OrderCompletionStageResolver.Result r = running("200", "0", "0", "200");

        assertEquals(OrderCompletionStageResolver.READY_TO_COMPLETE, r.stage());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.shortfallQuantity()));
    }

    @Test
    void overproductionHasNoShortfall() {
        OrderCompletionStageResolver.Result r = running("205", "0", "0", "200");

        assertEquals(OrderCompletionStageResolver.READY_TO_COMPLETE, r.stage());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.shortfallQuantity()));
    }

    @Test
    void missingPlannedQuantityDoesNotInventShortfall() {
        OrderCompletionStageResolver.Result r = running("0", "0", "0", null);

        assertEquals(OrderCompletionStageResolver.READY_TO_COMPLETE, r.stage());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.shortfallQuantity()));
    }

    @Test
    void pendingFqcLotsWinOverShortfall() {
        OrderCompletionStageResolver.Result r = OrderCompletionStageResolver.resolve(
                in(ProductionOrderStatusEnum.IN_PROGRESS.getValue(), false, 2, 2, true, 1,
                        "0", "0", "0", "200"));

        assertEquals(OrderCompletionStageResolver.PENDING_FQC, r.stage());
        assertTrue(r.nextAction().contains("判定完工检验"), r.nextAction());
    }

    @Test
    void noLotYetAsksForLotCreation() {
        OrderCompletionStageResolver.Result r = OrderCompletionStageResolver.resolve(
                in(ProductionOrderStatusEnum.IN_PROGRESS.getValue(), false, 2, 2, false, 0,
                        "0", "0", "0", "200"));

        assertEquals(OrderCompletionStageResolver.PENDING_FQC, r.stage());
        assertTrue(r.nextAction().contains("自动建完工检验批"), r.nextAction());
    }

    @Test
    void workInProgressBranchKeepsProgressText() {
        OrderCompletionStageResolver.Result r = OrderCompletionStageResolver.resolve(
                in(ProductionOrderStatusEnum.IN_PROGRESS.getValue(), false, 2, 1, false, 0,
                        "0", "0", "0", "200"));

        assertEquals(OrderCompletionStageResolver.IN_PRODUCTION, r.stage());
        assertTrue(r.nextAction().contains("已完成 1/2"), r.nextAction());
    }

    @Test
    void lifecycleBranchesStayUnchanged() {
        assertEquals(OrderCompletionStageResolver.NOT_STARTED,
                OrderCompletionStageResolver.resolve(
                        in(ProductionOrderStatusEnum.DRAFT.getValue(), false, 0, 0, false, 0, "0", "0", "0", "200"))
                        .stage());
        assertEquals(OrderCompletionStageResolver.PAUSED,
                OrderCompletionStageResolver.resolve(
                        in(ProductionOrderStatusEnum.PAUSED.getValue(), false, 2, 2, true, 0, "0", "0", "0", "200"))
                        .stage());
        assertEquals(OrderCompletionStageResolver.COMPLETED,
                OrderCompletionStageResolver.resolve(
                        in(ProductionOrderStatusEnum.COMPLETED.getValue(), false, 2, 2, true, 0, "200", "0", "0", "200"))
                        .stage());
        assertEquals(OrderCompletionStageResolver.PENDING_INBOUND,
                OrderCompletionStageResolver.resolve(
                        in(ProductionOrderStatusEnum.COMPLETED.getValue(), true, 2, 2, true, 0, "200", "0", "0", "200"))
                        .stage());
        assertEquals(OrderCompletionStageResolver.CANCELLED,
                OrderCompletionStageResolver.resolve(
                        in(ProductionOrderStatusEnum.CANCELLED.getValue(), false, 2, 2, true, 0, "0", "0", "0", "200"))
                        .stage());
        assertEquals(OrderCompletionStageResolver.CANCELLED,
                OrderCompletionStageResolver.resolve(
                        in(ProductionOrderStatusEnum.CLOSED.getValue(), false, 2, 2, true, 0, "0", "0", "0", "200"))
                        .stage());
        assertEquals(OrderCompletionStageResolver.UNKNOWN,
                OrderCompletionStageResolver.resolve(in(null, false, 0, 0, false, 0, "0", "0", "0", "200")).stage());
    }

    @Test
    void shortfallHelperClampsAtZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(OrderCompletionStageResolver.shortfall(null, new BigDecimal("5"))));
        assertEquals(0, new BigDecimal("2").compareTo(
                OrderCompletionStageResolver.shortfall(new BigDecimal("200"), new BigDecimal("198"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OrderCompletionStageResolver.shortfall(new BigDecimal("200"), new BigDecimal("200"))));
    }
}
