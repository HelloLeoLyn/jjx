package com.jjx.production.service;

import com.jjx.production.domain.vo.OrderTraceVO;

/**
 * P4-B：生产履历只读查询服务（Trace Read Model）
 * <p>
 * Trace = 现有事实（Order/Execution/WorkReport/Quality）的只读投影。
 * 不新增 Trace 事实表，不修改任何业务状态，仅 SELECT。
 */
public interface TraceQueryService {

    /**
     * 生产订单完整履历（订单头 + 时间线事件，按 eventTime ASC → sourceRank ASC → sourceId ASC 排序）
     *
     * @param orderId 生产订单 ID
     */
    OrderTraceVO getOrderTrace(Long orderId);

    /**
     * 生产订单履历（支持按类别 / 工序执行过滤）
     *
     * @param orderId     生产订单 ID
     * @param category    事件类别过滤：ORDER / EXECUTION / WORK_REPORT / QUALITY（可空=全部）
     * @param executionId 工序执行过滤（可空=全部）
     */
    OrderTraceVO getOrderTrace(Long orderId, String category, Long executionId);
}
