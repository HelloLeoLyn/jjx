package com.jjx.production.enums;

/**
 * P4-B：Trace eventType 常量集合（V1 定稿，只含可可靠推导的事件）
 * <p>
 * 明确不实现（无可靠业务时间或父子关系，禁止启发式伪造）：
 * EXECUTION_PAUSED / EXECUTION_CANCELLED / EXECUTION_SKIPPED / QUALITY_REINSPECTION_CREATED
 */
public final class TraceEventType {

    private TraceEventType() {
    }

    // ============ 订单 ============
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_STARTED = "ORDER_STARTED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";

    // ============ 工序执行 ============
    public static final String EXECUTION_STARTED = "EXECUTION_STARTED";
    public static final String EXECUTION_COMPLETED = "EXECUTION_COMPLETED";

    // ============ 责任流转（来自 production_dispatch_log） ============
    public static final String DISPATCH_ASSIGNED = "DISPATCH_ASSIGNED";
    public static final String DISPATCH_DELEGATED = "DISPATCH_DELEGATED";
    public static final String DISPATCH_REASSIGNED = "DISPATCH_REASSIGNED";
    public static final String DISPATCH_RETURNED = "DISPATCH_RETURNED";
    public static final String DISPATCH_REJECTED = "DISPATCH_REJECTED";
    public static final String DISPATCH_COMPLETED = "DISPATCH_COMPLETED";

    // ============ 报工 ============
    public static final String WORK_REPORT_SUBMITTED = "WORK_REPORT_SUBMITTED";
    public static final String WORK_REPORT_CANCELLED = "WORK_REPORT_CANCELLED";

    // ============ 质量 ============
    public static final String QUALITY_CREATED = "QUALITY_CREATED";
    public static final String QUALITY_PASSED = "QUALITY_PASSED";
    public static final String QUALITY_FAILED = "QUALITY_FAILED";
}
