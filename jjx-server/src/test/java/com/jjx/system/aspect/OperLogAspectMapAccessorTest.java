package com.jjx.system.aspect;

import com.jjx.common.core.result.Result;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.LogSaveService;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class OperLogAspectMapAccessorTest {

    @Test
    void quotationFlowIdIsBoundToItsOwnBusinessLogAndPreservesDetail() {
        var aspect = new OperLogAspect(mock(LogSaveService.class),mock(QuotationMapper.class),mock(SysAttachmentMapper.class));
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute("jjx.quotationFlowLog",Map.of("quotationId",1L,"reviewFlowId","qf-2"));
        var log = new SysOperLog();
        log.setBizType("quotation"); log.setBizId("1");
        log.setDetail("{\"changes\":[\"已审核\"],\"attachments\":[]}");
        aspect.applyQuotationFlowDetail(log,request);
        var detail=cn.hutool.json.JSONUtil.parseObj(log.getDetail());
        assertEquals("qf-2",detail.getStr("reviewFlowId"));
        assertEquals("已审核",detail.getJSONArray("changes").getStr(0));
        log.setBizId("2"); log.setDetail(null);
        aspect.applyQuotationFlowDetail(log,request);
        assertNull(log.getDetail());
        log.setBizId("1"); log.setBizType("order");
        aspect.applyQuotationFlowDetail(log,request);
        assertNull(log.getDetail());
    }

    @Test
    void resolvesMapPropertyInsideResultData() {
        OperLogAspect aspect = new OperLogAspect(
                mock(LogSaveService.class),
                mock(QuotationMapper.class),
                mock(SysAttachmentMapper.class));
        StandardEvaluationContext context = OperLogAspect.createSpelContext();
        OperLogAspect.bindResult(context, Result.success(Map.of("transferNo", "TF2609210001")));
        SysOperLog operLog = new SysOperLog();

        aspect.applyDetail(context, "#result.data.transferNo", operLog);

        assertEquals("TF2609210001", operLog.getDetail());
    }
}
