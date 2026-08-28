package com.jjx.trace.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceReviewHistoryVO {
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
