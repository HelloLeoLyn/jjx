package com.jjx.trace.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核流水（review_flow / sales_quotation_flow 统一视图）。
 * 按需加载：点击流水中的审核行时按 bizType+bizId 拉取。
 */
@Data
public class TraceReviewVO {
    private String flowId;
    private Integer roundNo;
    private String actionCode;
    private String actionName;
    private String fromStatus;
    private String toStatus;
    private String operatorName;
    private String comment;
    private String attachmentIds;
    private LocalDateTime createTime;
}
