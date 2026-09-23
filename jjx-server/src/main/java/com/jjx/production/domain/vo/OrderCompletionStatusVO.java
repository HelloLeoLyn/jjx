package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单级完工（统一收口）状态投影。
 *
 * <p>2026-09-10：业务流程定为「一级负责人对整张工单一次收口」——工人只报工、上一级审批，
 * 完工不再是逐工序动作。本 VO 供 PC「工序执行」页 / 移动端决定「完成工单」按钮显隐。</p>
 *
 * <p>说明：是否满足逐工序前置（无待审报工/数量达标/无未分配剩余/子树完成）在点击收口时由
 * 服务端一次性聚合校验并返回阻断清单，此处只做轻量投影（避免列表页 N+1）。</p>
 */
@Data
public class OrderCompletionStatusVO {

    /** 工单ID */
    private Long orderId;

    /** 工单号 */
    private String orderNo;

    /** 待完工工序数（未终态：非 已完成/已跳过/已取消） */
    private int pendingExecutionCount;

    /** 当前用户是否有权收口：该工单全部「有根任务」的工序根负责人均为本人，或超级管理员 */
    private boolean authorized;

    /** 是否可点击收口（有权限 + 存在待完工工序） */
    private boolean canComplete;

    // ==================== 完工阶段（派生，不落库）—— dev-20260918-015 ====================

    /**
     * 完工阶段（派生自 4 个状态机）：
     * NOT_STARTED 未开工 / IN_PRODUCTION 生产中 / PENDING_FQC 待完工检验 /
     * PENDING_DISPOSITION 待不良处置 / READY_TO_COMPLETE 待完工确认 /
     * PENDING_INBOUND 待入库处理 / COMPLETED 已完成 / CANCELLED 已取消
     */
    private String stage;

    /** 阶段中文名（直接展示） */
    private String stageLabel;

    /** 下一步该谁做什么（一句话） */
    private String nextAction;

    /** 工序总数（不含已取消） */
    private int executionTotal;

    /** 已完成工序数（已完成/已跳过） */
    private int executionDone;

    /** 待检完工检验批张数 */
    private int fqcPendingCount;

    /** 未处置不良合计 */
    private BigDecimal undisposedFailQuantity = BigDecimal.ZERO;

    /** 成品检验合格累计 */
    private BigDecimal qualifiedQuantity = BigDecimal.ZERO;

    /** 计划数量 */
    private BigDecimal plannedQuantity = BigDecimal.ZERO;

    /** 已登记报废合计（有效批关联不良单 SCRAP DONE）—— dev-20260923-028 */
    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    /** 缺口 = max(0, 计划数量 − 合格累计)；&gt;0 且阶段=待补产时即为「还需补产多少件」—— dev-20260923-028 */
    private BigDecimal shortfallQuantity = BigDecimal.ZERO;
}
