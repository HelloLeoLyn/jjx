package com.jjx.trace.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.trace.domain.vo.TraceReviewVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;

import java.util.List;

public interface TraceService {
    /** 产品作业规范范围：历史模块名「作业规范版本」一并纳入，仅做范围过滤，不另建查询。 */
    String SCOPE_WORK_SPEC = "work_spec";

    /** 按 trace_id 查询操作流水（只查 sys_oper_log 主表，分页） */
    default PageResult<UnifiedTraceEventVO> getEvents(String traceId, int pageNum, int pageSize) {
        return getEvents(traceId, null, pageNum, pageSize);
    }

    /** 按 trace_id 查询操作流水；scope 为空不筛模块。 */
    PageResult<UnifiedTraceEventVO> getEvents(String traceId, String scope, int pageNum, int pageSize);

    /**
     * 按业务对象查询操作日志（产品/BOM/工艺路线等主数据无 trace_id，按 bizType+bizId 聚合）
     */
    default PageResult<UnifiedTraceEventVO> getEventsByBiz(String bizType, Long bizId, int pageNum, int pageSize) {
        return getEventsByBiz(bizType, bizId, null, pageNum, pageSize);
    }

    /** 同一查询入口；scope=work_spec 时只保留产品作业规范模块，其余事件与排序不变。 */
    PageResult<UnifiedTraceEventVO> getEventsByBiz(String bizType, Long bizId, String scope, int pageNum, int pageSize);

    /** 按业务单据查询审核流水（review_flow 与报价 sales_quotation_flow 统一返回） */
    List<TraceReviewVO> reviewList(String bizType, Long bizId);
}
