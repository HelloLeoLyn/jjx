package com.jjx.quality;

import com.jjx.common.enums.AllowedActionEnum;
import com.jjx.quality.service.support.AllowedActionResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态 × 动作唯一出处单测 —— dev-20260923-039。
 *
 * <p>固化的就是今晚连续踩的坑：失效批不给动作、作废单不给处置、悬空单只给「随批作废」、
 * 界面永不出现"点下去必失败"的按钮。</p>
 */
class AllowedActionResolverTest {

    // ==================== 检验批 ====================

    @Test
    void pendingLotCanInspectAndJudge() {
        List<AllowedActionEnum> a = AllowedActionResolver.forLot("PENDING", false, false);
        assertTrue(a.contains(AllowedActionEnum.LOT_INSPECT));
        assertTrue(a.contains(AllowedActionEnum.LOT_JUDGE));
        assertEquals(2, a.size());
    }

    @Test
    void judgedLotOnlyReinspectAndClosedLotOnlyReopen() {
        assertEquals(List.of(AllowedActionEnum.LOT_REINSPECT),
                AllowedActionResolver.forLot("JUDGED", false, false));
        assertEquals(List.of(AllowedActionEnum.LOT_REOPEN),
                AllowedActionResolver.forLot("CLOSED", false, false));
    }

    @Test
    void supersededLotHasNoActionAtAll() {
        // 034 的教训：界面不能给注定失败的动作
        assertTrue(AllowedActionResolver.forLot("JUDGED", true, false).isEmpty());
        assertTrue(AllowedActionResolver.forLot("CLOSED", true, false).isEmpty());
        assertTrue(AllowedActionResolver.forLot("PENDING", true, false).isEmpty());
        assertTrue(AllowedActionResolver.lotBlockReason("JUDGED", true).contains("已被后续复检版本取代"));
    }

    @Test
    void unknownStatusGivesNoAction() {
        assertTrue(AllowedActionResolver.forLot(null, false, false).isEmpty());
        assertTrue(AllowedActionResolver.forLot("WHATEVER", false, false).isEmpty());
    }

    // ==================== 不良单 ====================

    @Test
    void openNcrWithPendingQuantityCanDispose() {
        List<AllowedActionEnum> a = AllowedActionResolver.forNcr("PENDING", false, new BigDecimal("2"));
        assertEquals(List.of(AllowedActionEnum.NCR_DISPOSE), a);
        assertTrue(AllowedActionResolver.forNcr("DISPOSING", false, new BigDecimal("1"))
                .contains(AllowedActionEnum.NCR_DISPOSE));
    }

    @Test
    void danglingNcrOnSupersededLotOnlyOffersVoid() {
        // 今晚实测：NCR260923002 挂在已失效批上仍显示「处置」→ 现在只给「随批作废」
        List<AllowedActionEnum> a = AllowedActionResolver.forNcr("PENDING", true, new BigDecimal("2"));
        assertEquals(List.of(AllowedActionEnum.NCR_VOID_SUPERSEDED), a);
    }

    @Test
    void voidOrClosedOrZeroPendingNcrHasNoDispose() {
        assertTrue(AllowedActionResolver.forNcr("VOID", false, BigDecimal.ZERO).isEmpty());
        assertTrue(AllowedActionResolver.forNcr("CLOSED", false, new BigDecimal("2")).isEmpty());
        assertTrue(AllowedActionResolver.forNcr("PENDING", false, BigDecimal.ZERO).isEmpty());
    }

    @Test
    void disposeBlockReasonsAreSpecific() {
        assertTrue(AllowedActionResolver.ncrDisposeBlockReason("VOID", false, BigDecimal.ZERO, "NCR-1")
                .contains("已作废"));
        assertTrue(AllowedActionResolver.ncrDisposeBlockReason("PENDING", true, new BigDecimal("2"), "NCR-2")
                .contains("已被后续复检版本取代"));
        assertTrue(AllowedActionResolver.ncrDisposeBlockReason("CLOSED", false, BigDecimal.ZERO, "NCR-3")
                .contains("已结案"));
    }

    // ==================== 处置动作 ====================

    @Test
    void scrapDoneCanRevokeOthersCannot() {
        assertEquals(List.of(AllowedActionEnum.NCR_REVOKE),
                AllowedActionResolver.forNcrAction("SCRAP", "DONE"));
        assertTrue(AllowedActionResolver.forNcrAction("SCRAP", "VOID").isEmpty());
        assertTrue(AllowedActionResolver.forNcrAction("CONCESSION", "DONE").isEmpty());
    }

    /** dev-20260924-005：超阈值的报废进入待审批 → 下发「审批通过 / 驳回」；非报废/非待审批不下发 */
    @Test
    void scrapPendingApprovalOffersApproveAndReject() {
        assertEquals(List.of(AllowedActionEnum.NCR_SCRAP_APPROVE, AllowedActionEnum.NCR_SCRAP_REJECT),
                AllowedActionResolver.forNcrAction("SCRAP", "PENDING_APPROVAL"));
        assertTrue(AllowedActionResolver.forNcrAction("REWORK", "PENDING_APPROVAL").isEmpty());
        assertTrue(AllowedActionResolver.forNcrAction("CONCESSION", "PENDING_APPROVAL").isEmpty());
    }

    @Test
    void processingReworkCanSupplementButNotAfterDone() {
        // dev-20260923-043：返工退料落地后，在制返工再下发「返工退料」（净耗 = 补料 − 退料）
        assertEquals(List.of(AllowedActionEnum.NCR_REWORK_COMPLETE, AllowedActionEnum.NCR_REWORK_SUPPLEMENT,
                        AllowedActionEnum.NCR_REWORK_RETURN),
                AllowedActionResolver.forNcrAction("REWORK", "PROCESSING"));
        assertTrue(AllowedActionResolver.forNcrAction("REWORK", "DONE").isEmpty());
    }

    @Test
    void productionTaskActionsComeFromResolver() {
        assertEquals(List.of(AllowedActionEnum.TASK_FLOW, AllowedActionEnum.TASK_ASSIGN,
                        AllowedActionEnum.TASK_RETURN, AllowedActionEnum.TASK_RECALL),
                AllowedActionResolver.forProductionTask("ACTIVE", true, true, true,
                        BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(List.of(AllowedActionEnum.TASK_FLOW),
                AllowedActionResolver.forProductionTask("COMPLETED", true, true, true,
                        BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(List.of(AllowedActionEnum.TASK_FLOW),
                AllowedActionResolver.forProductionTask("ACTIVE", false, true, true,
                        BigDecimal.ONE, BigDecimal.ONE));
    }

    // ==================== 动作字典 ====================

    @Test
    void actionCodesAndPermissionsAreSingleSourced() {
        assertEquals(List.of("LOT_REINSPECT"), AllowedActionEnum.codesOf(
                List.of(AllowedActionEnum.LOT_REINSPECT)));
        assertEquals("quality:ncr:void-superseded",
                AllowedActionEnum.byCode("NCR_VOID_SUPERSEDED").getPermission());
        assertEquals(AllowedActionEnum.Domain.INVENTORY,
                AllowedActionEnum.byCode("NCR_REWORK_SUPPLEMENT").getDomain());
        assertTrue(AllowedActionEnum.codesOf(null).isEmpty());
    }
}
