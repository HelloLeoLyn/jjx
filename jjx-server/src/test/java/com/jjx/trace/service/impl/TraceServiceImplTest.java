package com.jjx.trace.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.ReviewFlowMapper;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TraceServiceImplTest {
    private TraceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TraceServiceImpl(null, null, null, null, new ObjectMapper());
    }

    @Test
    void aggregatesChangesAttachmentsAndReviewHistory() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 28, 10, 14, 0);
        SysOperLog log = oper(1L, time, "approveOrder", 4);
        log.setDetail("{\"changes\":[\"交货日期：09-05 → 09-08\"],\"attachments\":[{\"id\":7,\"fileName\":\"确认书.pdf\"}]}");
        ReviewFlow submit = review(10L, time.minusMinutes(1), 1, "SUBMIT", "草稿", "待审核");
        ReviewFlow approve = review(11L, time.plusSeconds(3), 1, "APPROVE", "审核中", "已审核");
        approve.setComment("同意，注意交期");
        approve.setAttachmentIds("[7]");

        List<UnifiedTraceEventVO> events = service.aggregateEvents("order", List.of(log),
                List.of(submit, approve), List.of(), List.of(attachment(7L, "确认书.pdf", time)));

        assertEquals(2, events.size());
        UnifiedTraceEventVO merged = events.get(1);
        assertEquals("审核通过", merged.getActionTitle());
        assertEquals(List.of("交货日期：09-05 → 09-08"), merged.getChanges());
        assertEquals(1, merged.getAttachments().size());
        assertEquals("同意，注意交期", merged.getComment());
    }

    @Test
    void parsesFixedDetailSchemaWhenEitherArrayIsEmpty() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 28, 10, 16);
        SysOperLog changesOnly = oper(4L, time, "update", 2);
        changesOnly.setDetail("{\"changes\":[\"字段变更\"],\"attachments\":[]}");
        SysOperLog attachmentsOnly = oper(5L, time.plusSeconds(1), "upload", 2);
        attachmentsOnly.setDetail("{\"changes\":[],\"attachments\":[{\"id\":9,\"fileName\":\"附件.pdf\"}]}");

        List<UnifiedTraceEventVO> events = service.aggregateEvents("order", List.of(changesOnly, attachmentsOnly),
                List.of(), List.of(), List.of());

        assertEquals(List.of("字段变更"), events.get(0).getChanges());
        assertTrue(events.get(0).getAttachments().isEmpty());
        assertTrue(events.get(1).getChanges().isEmpty());
        assertEquals(9L, events.get(1).getAttachments().get(0).getId());
    }

    @Test
    void keepsUnmatchedReviewAsStandaloneEventWithoutDuplicatingMatchedAction() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 28, 10, 20);
        SysOperLog rejectLog = oper(2L, time, "rejectOrder", 5);
        ReviewFlow reject = review(20L, time.plusSeconds(2), 1, "REJECT", "审核中", "已驳回");

        List<UnifiedTraceEventVO> events = service.aggregateEvents("order", List.of(rejectLog),
                List.of(reject), List.of(), List.of());

        assertEquals(1, events.size());
        assertEquals("oper-2", events.get(0).getEventId());
        assertEquals("审核驳回", events.get(0).getActionTitle());
        assertEquals(1, events.get(0).getRoundNo());
        assertEquals("REJECT", events.get(0).getActionCode());
    }

    @Test
    void sortsAscendingAndPaginatesOnServer() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 28, 10, 0);
        List<UnifiedTraceEventVO> sorted = service.aggregateEvents("order", List.of(
                        oper(3L, base.plusMinutes(3), "update", 2),
                        oper(1L, base.plusMinutes(1), "create", 0),
                        oper(2L, base.plusMinutes(2), "update", 1)),
                List.of(), List.of(), List.of());
        PageResult<UnifiedTraceEventVO> page = service.page(sorted, 2, 2);

        assertEquals(3, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("oper-3", page.getRecords().get(0).getEventId());
        assertEquals(2, page.getPageNum());
        assertEquals(2, page.getTotalPages());
    }

    private SysOperLog oper(Long id, LocalDateTime time, String url, Integer status) {
        SysOperLog log = new SysOperLog();
        log.setId(id);
        log.setCreateTime(time);
        log.setOperUrl(url);
        log.setBizType("order");
        log.setBizId("99");
        log.setBizStatus(status);
        log.setStatus(1);
        log.setBusinessType(2);
        log.setRealName("测试员");
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

    private SysAttachment attachment(Long id, String name, LocalDateTime time) {
        SysAttachment attachment = new SysAttachment();
        attachment.setId(id);
        attachment.setFileName(name);
        attachment.setCreateTime(time);
        return attachment;
    }
}
