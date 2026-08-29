package com.jjx.trace.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.trace.domain.vo.TraceReviewVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;

import java.util.List;

public interface TraceService {
    /** 按 trace_id 查询操作流水（只查 sys_oper_log 主表，分页） */
    PageResult<UnifiedTraceEventVO> getEvents(String traceId, int pageNum, int pageSize);

    /**
     * 按业务对象查询操作日志（产品/BOM/工艺路线等主数据无 trace_id，按 bizType+bizId 聚合）
     */
    PageResult<UnifiedTraceEventVO> getEventsByBiz(String bizType, Long bizId, int pageNum, int pageSize);

    /** 按业务单据查询审核流水（review_flow 与报价 sales_quotation_flow 统一返回） */
    List<TraceReviewVO> reviewList(String bizType, Long bizId);
}
