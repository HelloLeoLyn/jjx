package com.jjx.sales.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单评审/审核过程记录（前端「订单评审记录」打印页与详情页使用）。
 *
 * <p>2026-09-21（dev-20260921-010）：此前 /sales/order/review/records 读的是 sales_order_review
 * 表，而实际审核动作把它写进 review_flow（biz_type=sales_order），该表永远是空的 → 记录页空白。
 * 现改为直接从 review_flow 映射。</p>
 */
@Data
public class OrderReviewProcessVO {

    /** 流转记录ID（review_flow.flow_id） */
    private Long recordId;

    private Long orderId;

    private String orderNo;

    /** 轮次（review_flow.round_no） */
    private Integer reviewStage;

    /** 阶段/动作名（提交审核、审核通过、审核驳回…） */
    private String stageName;

    /** 操作人显示名 */
    private String reviewerName;

    /** 意见/备注 */
    private String reviewComment;

    private LocalDateTime reviewTime;

    /** 结果码：1 通过 / 2 驳回 / 0 提交 */
    private Integer reviewResult;

    /** 结果文案：通过 / 驳回 / 已提交 */
    private String resultDescription;
}
