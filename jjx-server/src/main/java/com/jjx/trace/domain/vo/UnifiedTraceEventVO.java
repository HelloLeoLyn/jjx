package com.jjx.trace.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一流水事件（只来自 sys_oper_log 主表）。
 * detail/operParam/operUrl 原文透传，由前端解析变更/附件/审核标志。
 */
@Data
public class UnifiedTraceEventVO {
    private String eventId;
    private LocalDateTime time;
    private Integer bizStatus;
    private String actionTitle;
    private String operatorName;
    private Integer result;
    private String traceId;
    private String module;
    private String bizType;
    private String bizId;
    private Integer businessType;
    private String operUrl;
    private String operParam;
    private String detail;
}
