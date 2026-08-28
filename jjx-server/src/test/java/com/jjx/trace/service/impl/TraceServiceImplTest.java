package com.jjx.trace.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.ReviewFlowMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.trace.domain.vo.TraceReviewVO;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceServiceImplTest {
    private TraceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TraceServiceImpl(null, null, null);
    }

    @Test
    void eventsQueryByTraceIdAndPaginateOnDatabase() {
        SysOperLog first = oper(1L, LocalDateTime.of(2026, 8, 28, 10, 0), "/sales/order/create", 0, 1);
        SysOperLog second = oper(2L, LocalDateTime.of(2026, 8, 28, 10, 5), "/sales/order/update", 1, 2);
        Page<SysOperLog> page = new Page<>(1, 20);
        page.setTotal(2);
        page.setRecords(List.of(first, second));
        SysOperLogMapper sysOperLogMapper = mapper(SysOperLogMapper.class, "selectPage", page, null);

        service = new TraceServiceImpl(sysOperLogMapper, null, null);
        PageResult<UnifiedTraceEventVO> result = service.getEvents("T-100", 1, 20);

        assertEquals(2, result.getTotal());
        assertEquals("oper-1", result.getRecords().get(0).getEventId());
        assertEquals("oper-2", result.getRecords().get(1).getEventId());
        assertEquals("创建", result.getRecords().get(0).getActionTitle());
        assertEquals("T-100", result.getRecords().get(0).getTraceId());
    }

    @Test
    void eventsPassThroughRawDetailAndOperUrlForFrontendParsing() {
        SysOperLog log = oper(3L, LocalDateTime.of(2026, 8, 28, 10, 14), "approveOrder", 4, 6);
        log.setDetail("{\"changes\":[\"交货日期：09-05 → 09-08\"],\"attachments\":[{\"id\":7,\"fileName\":\"确认书.pdf\"}]}");
        Page<SysOperLog> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(log));
        SysOperLogMapper sysOperLogMapper = mapper(SysOperLogMapper.class, "selectPage", page, null);

        service = new TraceServiceImpl(sysOperLogMapper, null, null);
        PageResult<UnifiedTraceEventVO> result = service.getEvents("T-101", 1, 20);

        UnifiedTraceEventVO event = result.getRecords().get(0);
        assertEquals("审核通过", event.getActionTitle());
        assertEquals("approveOrder", event.getOperUrl());
        assertTrue(event.getDetail().contains("\"changes\""));
        assertTrue(event.getDetail().contains("\"attachments\""));
    }

    @Test
    void reviewListMapsBizTypeToReviewFlowStorage() {
        ReviewFlow flow = review(10L, LocalDateTime.of(2026, 8, 28, 10, 13), 1, "SUBMIT", "草稿", "待审核");
        flow.setComment("请审核");
        int[] queries = {0};
        ReviewFlowMapper reviewFlowMapper = mapper(ReviewFlowMapper.class, "selectList", List.of(flow), queries);

        service = new TraceServiceImpl(null, reviewFlowMapper, null);
        List<TraceReviewVO> result = service.reviewList("order", 99L);

        assertEquals(1, queries[0]);
        assertEquals("rf-10", result.get(0).getFlowId());
        assertEquals(1, result.get(0).getRoundNo());
        assertEquals("请审核", result.get(0).getComment());
    }

    @Test
    void reviewListNormalizesQuotationRoundsOnRejectResubmit() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 28, 10, 0);
        QuotationFlowMapper quotationFlowMapper = mapper(QuotationFlowMapper.class, "selectByQuotationId",
                List.of(
                        qf(1L, base.plusMinutes(1), "SUBMIT"),
                        qf(2L, base.plusMinutes(2), "REJECT"),
                        qf(3L, base.plusMinutes(3), "SUBMIT"),
                        qf(4L, base.plusMinutes(4), "APPROVE")),
                null);

        service = new TraceServiceImpl(null, null, quotationFlowMapper);
        List<TraceReviewVO> result = service.reviewList("quotation", 5L);

        assertEquals(4, result.size());
        assertEquals(1, result.get(0).getRoundNo());
        assertEquals(1, result.get(1).getRoundNo());
        assertEquals(2, result.get(2).getRoundNo());
        assertEquals(2, result.get(3).getRoundNo());
        assertEquals("qf-1", result.get(0).getFlowId());
    }

    @Test
    void reviewListReturnsEmptyForModulesWithoutReviewFlow() {
        service = new TraceServiceImpl(null, null, null);
        assertTrue(service.reviewList("inbound", 7L).isEmpty());
    }

    private SysOperLog oper(Long id, LocalDateTime time, String url, Integer status, Integer businessType) {
        SysOperLog log = new SysOperLog();
        log.setId(id);
        log.setCreateTime(time);
        log.setOperUrl(url);
        log.setBizType("order");
        log.setBizId("99");
        log.setBizStatus(status);
        log.setStatus(1);
        log.setBusinessType(businessType);
        log.setRealName("测试员");
        log.setTraceId("T-100");
        return log;
    }

    private ReviewFlow review(Long id, LocalDateTime time, int round, String action,
                              String from, String to) {
        ReviewFlow flow = new ReviewFlow();
        flow.setFlowId(id);
        flow.setCreateTime(time);
        flow.setRoundNo(round);
        flow.setActionCode(action);
        flow.setActionName(action);
        flow.setFromStatus(from);
        flow.setToStatus(to);
        flow.setOperatorName("审核员");
        return flow;
    }

    private SalesQuotationFlow qf(Long id, LocalDateTime time, String action) {
        SalesQuotationFlow flow = new SalesQuotationFlow();
        flow.setFlowId(id);
        flow.setCreateTime(time);
        flow.setActionCode(action);
        flow.setActionName(action);
        flow.setOperatorName("审核员");
        return flow;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> type, String methodName, Object result, int[] calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals(methodName)) {
                if (calls != null) calls[0]++;
                return result;
            }
            return null;
        });
    }
}
