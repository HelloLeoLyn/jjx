package com.jjx.production.domain.vo;

import lombok.Data;

@Data
public class OrderStatisticsVO {
    private Long totalCount; // 总数
    private Long draftCount; // 草稿数
    private Long pendingApprovalCount; // 待审批数
    private Long approvedCount; // 已审批数
    private Long scheduledCount; // 已排产数
    private Long inProgressCount; // 生产中数
    private Long completedCount; // 已完成数
    private Long cancelledCount; // 已取消数
}
