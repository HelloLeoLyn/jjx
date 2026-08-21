package com.jjx.production.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * P4-B：生产履历时间线事件（只读投影，非事实表）
 * <p>
 * Trace = 现有事实（Order/Execution/WorkReport/Quality）的统一只读投影。
 * 事件由 TraceQueryService 从真实业务表推导，不落库、不修改任何业务状态。
 */
@Data
public class TraceEventVO {

    /** 事件类型（见 TraceEventType 常量） */
    private String eventType;

    /** 事件业务时间（真实业务时间，禁止用 updateTime 推断） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    /** 所属生产订单 */
    private Long orderId;

    /** 关联工序执行（可空） */
    private Long executionId;

    /** 关联报工（可空） */
    private Long workReportId;

    /** 关联质检单（可空） */
    private Long qualityInspectionId;

    /** 操作人 ID（快照，可空；部分来源只有姓名） */
    private Long actorId;

    /** 操作人姓名（历史快照，不反查 sys_user） */
    private String actorName;

    /** 短标题（如"工序完成"） */
    private String title;

    /** 详情（数量/质量结果/报工内容等展示信息） */
    private String description;

    /** 事件后状态（如 COMPLETED/pass/SUBMITTED） */
    private String status;

    /** 来源类型：ORDER/EXECUTION/WORK_REPORT/QUALITY */
    private String sourceType;

    /** 来源主键（orderId/executionId/logId/reportId/inspectionId） */
    private Long sourceId;
}
