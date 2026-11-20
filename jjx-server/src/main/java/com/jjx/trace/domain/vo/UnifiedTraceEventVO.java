package com.jjx.trace.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一流水事件（来自 sys_oper_log；BOM历史档案补全显式标注evidenceSource，不补写原日志）。
 * detail/operParam/operUrl 原文透传，由前端解析变更/附件/审核标志。
 */
@Data
public class UnifiedTraceEventVO {
    private String bomCode;
    private String bomVersion;
    private String evidenceSource;
    private java.util.List<String> relatedBomIds;
    private String eventId;
    private String groupId;
    private java.util.List<UnifiedTraceEventVO> children = null;
    private LocalDateTime time;
    private String bizStatus;
    private String action;
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
