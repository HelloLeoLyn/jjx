package com.jjx.production.service.impl;

import com.jjx.production.enums.ProductionOrderStatusEnum;

import java.math.BigDecimal;

/**
 * 工单完工阶段派生器（dev-20260918-015 抽出为纯函数 + dev-20260923-028 补「待补产」）。
 *
 * <p>「工序完成 ≠ 工单完成」：工序全完后阶段要翻成「待完工检验」而不是仍显示「进行中」。
 * 本类把这段判定从 {@code ProductionOperationExecutionServiceImpl.fillStage} 抽出，便于单测覆盖全部分支
 * （不查库、不落库）。分支顺序与语义与抽出前一致，只有一处口径修正：
 * 原来「良品累计 &lt; 计划」→ 待完工检验 /「继续检验、补检」，会把**因报废造成的缺口**误导成"要补检"；
 * 现在改为 {@link #PENDING_SUPPLEMENT}「待补产」，并带出一句人话原因与缺口数量（dev-20260923-028）。</p>
 */
public final class OrderCompletionStageResolver {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String IN_PRODUCTION = "IN_PRODUCTION";
    public static final String PENDING_FQC = "PENDING_FQC";
    public static final String PENDING_DISPOSITION = "PENDING_DISPOSITION";
    /** 新增：工序全完、无待检批、无未处置不良，但良品累计未达计划 → 需要补产（报废造成的缺口） */
    public static final String PENDING_SUPPLEMENT = "PENDING_SUPPLEMENT";
    public static final String READY_TO_COMPLETE = "READY_TO_COMPLETE";
    public static final String PENDING_INBOUND = "PENDING_INBOUND";
    public static final String COMPLETED = "COMPLETED";
    public static final String PAUSED = "PAUSED";
    public static final String CANCELLED = "CANCELLED";

    private OrderCompletionStageResolver() {
    }

    /** 判定入参（全部为投影值，不落库） */
    public record Input(Integer orderStatus,
                        boolean inboundPendingFlag,
                        int executionTotal,
                        int executionDone,
                        boolean hasLot,
                        int fqcPendingCount,
                        BigDecimal qualifiedTotal,
                        BigDecimal undisposedFailQuantity,
                        BigDecimal scrappedTotal,
                        BigDecimal plannedQuantity) {
    }

    /** 判定结果：阶段码 / 中文名 / 下一步一句话 / 缺口（计划 − 良品累计，下限 0） */
    public record Result(String stage, String label, String nextAction, BigDecimal shortfallQuantity) {
    }

    /** 缺口 = max(0, 计划 − 良品累计) */
    public static BigDecimal shortfall(BigDecimal planned, BigDecimal qualifiedTotal) {
        BigDecimal p = nz(planned);
        BigDecimal q = nz(qualifiedTotal);
        BigDecimal gap = p.subtract(q);
        return gap.signum() > 0 ? gap : BigDecimal.ZERO;
    }

    public static Result resolve(Input in) {
        BigDecimal zero = BigDecimal.ZERO;
        if (in == null || in.orderStatus() == null) {
            return new Result(UNKNOWN, "未知", "工单不存在", zero);
        }
        Integer status = in.orderStatus();
        BigDecimal planned = nz(in.plannedQuantity());
        BigDecimal qualified = nz(in.qualifiedTotal());
        BigDecimal scrap = nz(in.scrappedTotal());
        BigDecimal gap = shortfall(planned, qualified);

        if (ProductionOrderStatusEnum.CANCELLED.getValue().equals(status)
                || ProductionOrderStatusEnum.CLOSED.getValue().equals(status)) {
            return new Result(CANCELLED, "已取消/已关闭", "—", zero);
        }
        if (ProductionOrderStatusEnum.COMPLETED.getValue().equals(status) && in.inboundPendingFlag()) {
            return new Result(PENDING_INBOUND, "待入库处理", "重试完工入库（生产/仓库）", zero);
        }
        if (ProductionOrderStatusEnum.COMPLETED.getValue().equals(status)) {
            return new Result(COMPLETED, "已完成", "工单已完工", zero);
        }
        if (ProductionOrderStatusEnum.PAUSED.getValue().equals(status)) {
            return new Result(PAUSED, "已暂停", "恢复生产后继续", zero);
        }
        if (!ProductionOrderStatusEnum.IN_PROGRESS.getValue().equals(status)) {
            return new Result(NOT_STARTED, "未开工", "开工后进入生产", zero);
        }
        boolean allDone = in.executionTotal() > 0 && in.executionDone() >= in.executionTotal();
        if (!allDone) {
            return new Result(IN_PRODUCTION, "生产中",
                    "继续工序执行/报工（已完成 " + in.executionDone() + "/" + in.executionTotal() + "）", zero);
        }
        if (!in.hasLot()) {
            return new Result(PENDING_FQC, "待完工检验", "末道工序报工审批通过后自动建完工检验批（质检）", zero);
        }
        if (in.fqcPendingCount() > 0) {
            return new Result(PENDING_FQC, "待完工检验", "判定完工检验（质检员）", gap);
        }
        if (nz(in.undisposedFailQuantity()).signum() > 0) {
            return new Result(PENDING_DISPOSITION, "待不良处置", "返工/报废处置未清（生产/质检）", gap);
        }
        if (gap.signum() > 0) {
            String reason = scrap.signum() > 0
                    ? "检验报废 " + plain(scrap) + " 件已处置，良品 " + plain(qualified) + "/计划 " + plain(planned)
                            + "，还缺 " + plain(gap) + " 件 → 到末道工序「补报」补产"
                    : "完工检验合格累计 " + plain(qualified) + " 未达计划 " + plain(planned)
                            + "，还缺 " + plain(gap) + " 件 → 到末道工序「补报」补产";
            return new Result(PENDING_SUPPLEMENT, "待补产", reason, gap);
        }
        return new Result(READY_TO_COMPLETE, "待完工确认", "点「完工工单」收口（一级负责人）", zero);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String plain(BigDecimal v) {
        return nz(v).stripTrailingZeros().toPlainString();
    }
}
