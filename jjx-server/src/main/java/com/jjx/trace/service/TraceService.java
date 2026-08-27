package com.jjx.trace.service;

import java.util.List;
import java.util.Map;

public interface TraceService {
    /** 按trace_id查询完整业务链路 */
    List<Map<String, Object>> getTraceByTraceId(String traceId);

    /** 按业务编号反查trace_id */
    List<Map<String, Object>> searchTrace(String keyword);
}
