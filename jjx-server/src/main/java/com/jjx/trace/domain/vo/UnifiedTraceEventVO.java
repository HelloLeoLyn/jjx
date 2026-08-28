package com.jjx.trace.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UnifiedTraceEventVO {
    private String eventId;
    private LocalDateTime time;
    private Integer bizStatus;
    private String actionTitle;
    private String operatorName;
    private Integer result;
    private List<String> changes = new ArrayList<>();
    private List<TraceAttachmentVO> attachments = new ArrayList<>();
    private List<TraceReviewHistoryVO> reviewHistory = new ArrayList<>();
    private Integer roundNo;
    private String traceId;
    private String module;
    private String bizType;
    private Integer businessType;
    private String actionCode;
}
