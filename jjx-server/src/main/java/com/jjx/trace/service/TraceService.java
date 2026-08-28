package com.jjx.trace.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;

import java.util.List;
import java.util.Map;

public interface TraceService {
    /** 按trace_id查询完整业务链路 */
    List<Map<String, Object>> getTraceByTraceId(String traceId);

    /** 按业务编号反查trace_id */
    List<Map<String, Object>> searchTrace(String keyword);

    /** 按业务单据聚合操作、审核与附件事件。 */
    PageResult<UnifiedTraceEventVO> getEvents(String bizType, Long bizId, int pageNum, int pageSize);
}
