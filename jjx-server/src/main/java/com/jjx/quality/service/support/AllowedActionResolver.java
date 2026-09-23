package com.jjx.quality.service.support;

import com.jjx.common.enums.AllowedActionEnum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 状态 × 动作**唯一出处** —— dev-20260923-039（设计：quality-disposition-rework-master-dev-20260923-045.md §2.3）。
 *
 * <p>规则：对每个对象按「对象 × 状态 × 链路有效性」算出 allowedActions；
 * ① 接口把它下发给前端（页面只按它渲染，不写状态条件）；
 * ② 服务端守卫调**同一个方法**判断能否执行，并用同一处的文案解释拒绝原因。</p>
 *
 * <p>入参一律是**值**（不依赖实体），因此质量域与生产域都能调用：这是"唯一出处"优先于包结构整洁的取舍。</p>
 */
public final class AllowedActionResolver {

    private AllowedActionResolver() {
    }

    // ==================== 检验批 ====================

    /**
     * 检验批可用动作。
     *
     * @param status        批状态：PENDING / INSPECTING / JUDGED / CLOSED
     * @param superseded    是否已被后继复检版本取代（派生：存在子批）
     * @param hasOpenDefect 是否还有未处置不良（有则引导去不良台账，不给批级动作）
     */
    public static List<AllowedActionEnum> forLot(String status, boolean superseded, boolean hasOpenDefect) {
        if (superseded) {
            // 失效批一律只读（034 的教训：界面不能给注定失败的动作）
            return List.of();
        }
        if (hasOpenDefect) {
            // 有未处置不良 → 不给批级动作，引导去不良台账处置（方案 §3.2 总表；039 第二片接线）
            return List.of();
        }
        List<AllowedActionEnum> actions = new ArrayList<>();
        String s = status == null ? "" : status.trim().toUpperCase();
        switch (s) {
            case "PENDING", "INSPECTING" -> {
                actions.add(AllowedActionEnum.LOT_INSPECT);
                actions.add(AllowedActionEnum.LOT_JUDGE);
            }
            case "JUDGED" -> actions.add(AllowedActionEnum.LOT_REINSPECT);
            case "CLOSED" -> actions.add(AllowedActionEnum.LOT_REOPEN);
            default -> {
                // 未知状态：不给动作（宁可少给，也不给注定失败的动作）
            }
        }
        return actions;
    }

    /** 检验批被拒绝时的统一文案（守卫与前端提示同源） */
    public static String lotBlockReason(String status, boolean superseded) {
        return lotBlockReason(status, superseded, false);
    }

    /** 同上，含「有未处置不良」分支（dev-20260923-039 第二片：批级动作让位给不良台账） */
    public static String lotBlockReason(String status, boolean superseded, boolean hasOpenDefect) {
        if (superseded) {
            return "该批已被后续复检版本取代（已失效），只能查看/打印——请对最新版本操作";
        }
        if (hasOpenDefect) {
            return "该批还有未处置的不良，请先到「产品不良台账」处置完再操作本批";
        }
        String s = status == null ? "" : status.trim().toUpperCase();
        return switch (s) {
            case "PENDING", "INSPECTING" -> "该批尚未判定，请先「判定」";
            case "JUDGED" -> "该批已判定，如需更正请「复检」";
            case "CLOSED" -> "该批已关闭，如需更正请先「重开」";
            default -> "该批当前状态不允许该动作";
        };
    }

    // ==================== 不良单 ====================

    /**
     * 不良单可用动作（行级）。
     *
     * @param ncrStatus            PENDING / DISPOSING / CLOSED / VOID
     * @param sourceLotSuperseded  来源检验批是否已被后继版本取代
     * @param pendingQuantity      待处置数量（不良 − 已处置，已作废/已结视为 0）
     */
    public static List<AllowedActionEnum> forNcr(String ncrStatus, boolean sourceLotSuperseded,
                                                 BigDecimal pendingQuantity) {
        List<AllowedActionEnum> actions = new ArrayList<>();
        String s = ncrStatus == null ? "" : ncrStatus.trim().toUpperCase();
        boolean open = "PENDING".equals(s) || "DISPOSING".equals(s);
        if (open && !sourceLotSuperseded && nz(pendingQuantity).signum() > 0) {
            actions.add(AllowedActionEnum.NCR_DISPOSE);
        }
        // 随批作废（块 3 的正式入口）：只对"来源批已失效、却还没作废"的悬空单提供
        if (open && sourceLotSuperseded) {
            actions.add(AllowedActionEnum.NCR_VOID_SUPERSEDED);
        }
        return actions;
    }

    /** 不良单被拒绝处置时的统一文案 */
    public static String ncrDisposeBlockReason(String ncrStatus, boolean sourceLotSuperseded,
                                               BigDecimal pendingQuantity, String ncrNo) {
        String no = ncrNo == null ? "" : ncrNo;
        String s = ncrStatus == null ? "" : ncrStatus.trim().toUpperCase();
        if ("VOID".equals(s)) {
            return "该不良单已作废（随批失效/撤销），禁止再处置：" + no;
        }
        if (sourceLotSuperseded) {
            return "该不良单的来源检验批已被后续复检版本取代，禁止处置（请对最新有效版本操作）：" + no;
        }
        if (!"PENDING".equals(s) && !"DISPOSING".equals(s)) {
            return "该不良单已结案，无需处置：" + no;
        }
        return "该不良单待处置数量为 0，无需处置：" + no;
    }

    // ==================== 处置动作 ====================

    /**
     * 处置动作可用动作（处置记录行级）。
     *
     * @param actionType    REWORK / CONCESSION / SCRAP / RETURN
     * @param actionStatus  PENDING / PROCESSING / DONE / VOID
     */
    public static List<AllowedActionEnum> forNcrAction(String actionType, String actionStatus) {
        List<AllowedActionEnum> actions = new ArrayList<>();
        String type = actionType == null ? "" : actionType.trim().toUpperCase();
        String status = actionStatus == null ? "" : actionStatus.trim().toUpperCase();
        // 撤销：当前仅支持"报废 DONE"（无库存影响，可安全反做；让步/返工需反向库存，见 045 §6 第 7 行）
        if ("SCRAP".equals(type) && "DONE".equals(status)) {
            actions.add(AllowedActionEnum.NCR_REVOKE);
        }
        // 返工：在制中可补料；退料在 dev-20260923-043 落地后再放开（避免出现点不动的按钮）
        if ("REWORK".equals(type) && ("PENDING".equals(status) || "PROCESSING".equals(status))) {
            actions.add(AllowedActionEnum.NCR_REWORK_COMPLETE);
            actions.add(AllowedActionEnum.NCR_REWORK_SUPPLEMENT);
        }
        return actions;
    }

    /** 生产任务树动作投影：身份、状态、数量与树结构规则的唯一出处。 */
    public static List<AllowedActionEnum> forProductionTask(String status, boolean operator,
                                                             boolean assignAllowed, boolean hasParent,
                                                             BigDecimal remaining, BigDecimal childAssigned) {
        List<AllowedActionEnum> actions = new ArrayList<>();
        actions.add(AllowedActionEnum.TASK_FLOW);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        boolean active = "PENDING".equals(normalized) || "ACTIVE".equals(normalized);
        if (!active || !operator) {
            return actions;
        }
        if (assignAllowed && nz(remaining).signum() > 0) {
            actions.add(AllowedActionEnum.TASK_ASSIGN);
        }
        if (hasParent && nz(remaining).signum() > 0) {
            actions.add(AllowedActionEnum.TASK_RETURN);
        }
        if (nz(childAssigned).signum() > 0) {
            actions.add(AllowedActionEnum.TASK_RECALL);
        }
        return actions;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
