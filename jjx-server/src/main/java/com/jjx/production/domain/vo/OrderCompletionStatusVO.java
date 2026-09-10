package com.jjx.production.domain.vo;

import lombok.Data;

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
}
